package app.trainer.backend.schedule

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val PUSH_SLOT_ID_KEY = "slotId"
private const val PERSONAL_SLOT_CAPACITY = 1

@Service
class ScheduleService(
    private val slotRepository: TrainingSlotRepository,
    private val changeRequestRepository: SlotChangeRequestRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val userRepository: UserRepository,
    private val waitlistRepository: SlotWaitlistRepository,
    private val participantRepository: SlotParticipantRepository,
    private val pushSender: PushSender,
    private val clock: Clock,
) {

    @Transactional
    fun createSlot(coachUserId: UUID, request: CreateSlotRequest): CoachSlotResponse {
        val coach = requireCoach(coachUserId)
        if (hasOverlap(coachId = coach.id, startsAt = request.startsAt, durationMinutes = request.durationMinutes)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Слот пересекается с существующим")
        }
        val slot = saveFreeSlot(
            coachId = coach.id,
            startsAt = request.startsAt,
            durationMinutes = request.durationMinutes,
            capacity = request.capacity ?: PERSONAL_SLOT_CAPACITY,
        )
        return toCoachResponse(slot = slot, pendingRequestId = null)
    }

    @Transactional
    fun createSlotSeries(coachUserId: UUID, request: CreateSlotSeriesRequest): CreateSlotSeriesResponse {
        val coach = requireCoach(coachUserId)
        val zone = coachZone(coach)
        val created = mutableListOf<CoachSlotResponse>()
        val skipped = mutableListOf<SkippedSlotResponse>()

        seriesStarts(request = request, zone = zone).forEach { startsAt ->
            val overlaps = hasOverlap(
                coachId = coach.id,
                startsAt = startsAt,
                durationMinutes = request.durationMinutes,
            )
            if (overlaps) {
                skipped.add(
                    SkippedSlotResponse(startsAt = startsAt, reason = SkipReason.OVERLAPS_EXISTING_SLOT)
                )
            } else {
                val slot = saveFreeSlot(
                    coachId = coach.id,
                    startsAt = startsAt,
                    durationMinutes = request.durationMinutes,
                    capacity = request.capacity ?: PERSONAL_SLOT_CAPACITY,
                )
                created.add(toCoachResponse(slot = slot, pendingRequestId = null))
            }
        }
        return CreateSlotSeriesResponse(created = created, skipped = skipped)
    }

    @Transactional(readOnly = true)
    fun coachSchedule(coachUserId: UUID, from: Instant, to: Instant): CoachScheduleResponse {
        val coach = requireCoach(coachUserId)
        val slots = slotRepository.findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(
            coachId = coach.id,
            from = from,
            to = to,
        )
        val pendingBySlot = pendingRequestIdsFor(slots)
        val participantsBySlot = participantsOf(slots)
        return CoachScheduleResponse(
            coachId = coach.id,
            zoneId = coach.zoneId,
            slots = slots.map { slot ->
                toCoachResponse(
                    slot = slot,
                    pendingRequestId = pendingBySlot[slot.id],
                    participants = participantsBySlot[slot.id].orEmpty(),
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun clientSchedule(userId: UUID, coachId: UUID, from: Instant, to: Instant): ClientScheduleResponse {
        requireActiveCoachClient(coachId = coachId, userId = userId)
        val coach = coachRepository.findByIdOrNull(coachId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Тренер не найден")
        val slots = slotRepository.findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(
            coachId = coachId,
            from = from,
            to = to,
        )
        val pendingBySlot = pendingRequestIdsFor(slots)
        val seatsBySlot = seatsTakenIn(slots)
        val mySlotIds = participantRepository
            .findBySlotIdIn(slots.map { it.id })
            .filter { it.userId == userId }
            .map { it.slotId }
            .toSet()
        val waitlistedSlotIds = waitlistRepository
            .findBySlotIdInAndUserId(slotIds = slots.map { it.id }, userId = userId)
            .map { it.slotId }
            .toSet()
        return ClientScheduleResponse(
            coachId = coachId,
            zoneId = coach.zoneId,
            cancellationWindowHours = coach.cancellationWindowHours,
            slots = slots
                .filter { it.lifecycle != SlotLifecycle.CANCELLED }
                .map { slot ->
                    toClientResponse(
                        slot = slot,
                        isMine = mySlotIds.contains(slot.id),
                        takenSeats = seatsBySlot[slot.id] ?: 0,
                        pendingBySlot = pendingBySlot,
                        cancellationWindowHours = coach.cancellationWindowHours,
                        isOnWaitlist = waitlistedSlotIds.contains(slot.id),
                    )
                },
        )
    }

    @Transactional
    fun assignSlot(coachUserId: UUID, slotId: UUID, clientUserId: UUID): CoachSlotResponse {
        val coach = requireCoach(coachUserId)
        val slot = slotRepository.findWithLockById(slotId) ?: slotNotFound()
        requireSlotOwnedBy(slot = slot, coach = coach)
        requireActiveCoachClient(coachId = coach.id, userId = clientUserId)
        if (slot.lifecycle == SlotLifecycle.COMPLETED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Тренировка уже проведена")
        }
        takeSeat(slot = slot, userId = clientUserId)
        return toCoachResponse(slot = slot, pendingRequestId = null)
    }

    @Transactional
    fun cancelSlot(coachUserId: UUID, slotId: UUID): CoachSlotResponse {
        val coach = requireCoach(coachUserId)
        val slot = slotRepository.findWithLockById(slotId) ?: slotNotFound()
        requireSlotOwnedBy(slot = slot, coach = coach)
        slot.lifecycle = SlotLifecycle.CANCELLED
        rejectPendingRequest(slotId = slot.id)
        return toCoachResponse(slot = slot, pendingRequestId = null)
    }

    @Transactional
    fun releaseBookingsOf(coachId: UUID, clientUserId: UUID) {
        slotRepository
            .findParticipatedAfter(
                coachId = coachId,
                userId = clientUserId,
                startsAt = Instant.now(clock),
            )
            .filter { it.lifecycle == SlotLifecycle.SCHEDULED }
            .forEach { slot ->
                rejectPendingRequest(slotId = slot.id)
                freeSeat(slot = slot, userId = clientUserId)
            }
    }

    @Transactional
    fun completeSlot(coachUserId: UUID, slotId: UUID): CoachSlotResponse {
        val coach = requireCoach(coachUserId)
        val slot = slotRepository.findWithLockById(slotId) ?: slotNotFound()
        requireSlotOwnedBy(slot = slot, coach = coach)
        if (seatsTakenIn(slot.id) == 0) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "На слот никто не записан")
        }
        slot.lifecycle = SlotLifecycle.COMPLETED
        rejectPendingRequest(slotId = slot.id)
        return toCoachResponse(slot = slot, pendingRequestId = null)
    }

    @Transactional
    fun book(userId: UUID, slotId: UUID): ClientSlotResponse {
        val slot = slotRepository.findWithLockById(slotId) ?: slotNotFound()
        requireActiveCoachClient(coachId = slot.coachId, userId = userId)
        takeSeat(slot = slot, userId = userId)
        val entry = waitlistRepository.findBySlotIdAndUserId(slotId = slot.id, userId = userId)
        if (entry != null) waitlistRepository.delete(entry)
        return clientResponseOf(slot = slot, userId = userId)
    }

    @Transactional
    fun joinWaitlist(userId: UUID, slotId: UUID): ClientSlotResponse {
        val slot = slotRepository.findByIdOrNull(slotId) ?: slotNotFound()
        requireActiveCoachClient(coachId = slot.coachId, userId = userId)
        if (statusOf(slot = slot, takenSeats = seatsTakenIn(slot.id)) == SlotStatus.FREE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Свободные места есть, можно записаться сразу")
        }
        if (participantRepository.findBySlotIdAndUserId(slotId = slot.id, userId = userId) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Вы уже записаны на это занятие")
        }
        if (waitlistRepository.findBySlotIdAndUserId(slotId = slotId, userId = userId) == null) {
            waitlistRepository.save(
                SlotWaitlistEntity(
                    id = UUID.randomUUID(),
                    slotId = slotId,
                    userId = userId,
                    createdAt = Instant.now(clock),
                    notifiedAt = null,
                )
            )
        }
        return clientResponseOf(slot = slot, userId = userId, isOnWaitlist = true)
    }

    @Transactional
    fun leaveWaitlist(userId: UUID, slotId: UUID): ClientSlotResponse {
        val slot = slotRepository.findByIdOrNull(slotId) ?: slotNotFound()
        val entry = waitlistRepository.findBySlotIdAndUserId(slotId = slotId, userId = userId)
        if (entry != null) waitlistRepository.delete(entry)
        return clientResponseOf(slot = slot, userId = userId)
    }

    private fun clientResponseOf(
        slot: TrainingSlotEntity,
        userId: UUID,
        isOnWaitlist: Boolean = false,
    ): ClientSlotResponse {
        val coach = coachRepository.findByIdOrNull(slot.coachId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Тренер не найден")
        return toClientResponse(
            slot = slot,
            isMine = participantRepository.findBySlotIdAndUserId(slotId = slot.id, userId = userId) != null,
            takenSeats = seatsTakenIn(slot.id),
            pendingBySlot = emptyMap(),
            cancellationWindowHours = coach.cancellationWindowHours,
            isOnWaitlist = isOnWaitlist,
        )
    }

    private fun notifyWaitlist(slot: TrainingSlotEntity) {
        val waiting = waitlistRepository.findBySlotIdOrderByCreatedAtAsc(slot.id)
        if (waiting.isEmpty()) return
        val now = Instant.now(clock)
        waiting.forEach { entry -> entry.notifiedAt = now }
        pushSender.send(
            userIds = waiting.map { it.userId },
            message = PushMessage(
                channel = PushChannel.SCHEDULE,
                text = PushText.WAITLIST_SLOT_FREED,
                data = mapOf(PUSH_SLOT_ID_KEY to slot.id.toString()),
            ),
        )
    }

    @Transactional
    fun requestChange(userId: UUID, slotId: UUID, body: SlotChangeRequestBody): SlotChangeRequestResponse {
        val slot = slotRepository.findByIdOrNull(slotId) ?: slotNotFound()
        if (participantRepository.findBySlotIdAndUserId(slotId = slot.id, userId = userId) == null) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Вы не записаны на это занятие")
        }
        if (slot.lifecycle != SlotLifecycle.SCHEDULED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "По этому слоту заявку подать нельзя")
        }
        if (body.kind == SlotChangeKind.RESCHEDULE && slot.capacity > PERSONAL_SLOT_CAPACITY) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Групповое занятие переносит тренер: вы можете только отменить своё участие",
            )
        }
        if (body.kind == SlotChangeKind.RESCHEDULE && body.proposedStartsAt == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Для переноса нужно новое время")
        }
        val coach = coachRepository.findByIdOrNull(slot.coachId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Тренер не найден")
        if (!isWithinChangeWindow(slot = slot, cancellationWindowHours = coach.cancellationWindowHours)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Изменить запись можно не позднее чем за ${coach.cancellationWindowHours} ч до начала",
            )
        }
        val alreadyPending = changeRequestRepository.findBySlotIdAndStatus(
            slotId = slotId,
            status = SlotChangeStatus.PENDING,
        )
        if (alreadyPending != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "По слоту уже есть заявка на рассмотрении")
        }
        val request = changeRequestRepository.save(
            SlotChangeRequestEntity(
                id = UUID.randomUUID(),
                slotId = slotId,
                requestedByUserId = userId,
                kind = body.kind,
                proposedStartsAt = body.proposedStartsAt,
                status = SlotChangeStatus.PENDING,
                createdAt = Instant.now(clock),
                resolvedAt = null,
            )
        )
        return toResponse(request = request, slot = slot)
    }

    @Transactional(readOnly = true)
    fun pendingChangeRequests(coachUserId: UUID): List<SlotChangeRequestResponse> {
        val coach = requireCoach(coachUserId)
        return changeRequestRepository
            .findByCoachIdAndStatus(coachId = coach.id, status = SlotChangeStatus.PENDING.name)
            .mapNotNull { request ->
                val slot = slotRepository.findByIdOrNull(request.slotId) ?: return@mapNotNull null
                toResponse(request = request, slot = slot)
            }
    }

    @Transactional
    fun resolveChange(coachUserId: UUID, requestId: UUID, approve: Boolean): SlotChangeRequestResponse {
        val coach = requireCoach(coachUserId)
        val request = changeRequestRepository.findByIdOrNull(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена")
        if (request.status != SlotChangeStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже рассмотрена")
        }
        val slot = slotRepository.findWithLockById(request.slotId) ?: slotNotFound()
        requireSlotOwnedBy(slot = slot, coach = coach)

        request.status = if (approve) SlotChangeStatus.APPROVED else SlotChangeStatus.REJECTED
        request.resolvedAt = Instant.now(clock)
        if (approve) applyChange(slot = slot, request = request)
        return toResponse(request = request, slot = slot)
    }

    private fun applyChange(slot: TrainingSlotEntity, request: SlotChangeRequestEntity) {
        when (request.kind) {
            SlotChangeKind.CANCEL -> freeSeat(slot = slot, userId = request.requestedByUserId)
            SlotChangeKind.RESCHEDULE -> {
                val proposed = request.proposedStartsAt
                    ?: throw ResponseStatusException(HttpStatus.CONFLICT, "В заявке нет нового времени")
                val overlaps = hasOverlap(
                    coachId = slot.coachId,
                    startsAt = proposed,
                    durationMinutes = slot.durationMinutes,
                    excludedSlotId = slot.id,
                )
                if (overlaps) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "Новое время пересекается с другим слотом")
                }
                slot.startsAt = proposed
            }
        }
    }

    private fun rejectPendingRequest(slotId: UUID) {
        val pending = changeRequestRepository.findBySlotIdAndStatus(
            slotId = slotId,
            status = SlotChangeStatus.PENDING,
        ) ?: return
        pending.status = SlotChangeStatus.REJECTED
        pending.resolvedAt = Instant.now(clock)
    }

    private fun seriesStarts(request: CreateSlotSeriesRequest, zone: ZoneId): List<Instant> {
        val endDate = request.startDate.plusWeeks(request.weeksCount.toLong())
        val starts = mutableListOf<Instant>()
        var date: LocalDate = request.startDate
        while (date.isBefore(endDate)) {
            if (date.dayOfWeek in request.daysOfWeek) {
                starts.add(date.atTime(request.timeOfDay).atZone(zone).toInstant())
            }
            date = date.plusDays(1)
        }
        return starts
    }

    private fun saveFreeSlot(
        coachId: UUID,
        startsAt: Instant,
        durationMinutes: Int,
        capacity: Int,
    ): TrainingSlotEntity {
        return slotRepository.save(
            TrainingSlotEntity(
                id = UUID.randomUUID(),
                coachId = coachId,
                startsAt = startsAt,
                durationMinutes = durationMinutes,
                capacity = capacity,
                lifecycle = SlotLifecycle.SCHEDULED,
                createdAt = Instant.now(clock),
            )
        )
    }

    private fun seatsTakenIn(slotId: UUID): Int = participantRepository.countBySlotId(slotId)

    private fun statusOf(slot: TrainingSlotEntity, takenSeats: Int): SlotStatus = when (slot.lifecycle) {
        SlotLifecycle.CANCELLED -> SlotStatus.CANCELLED
        SlotLifecycle.COMPLETED -> SlotStatus.COMPLETED
        SlotLifecycle.SCHEDULED -> if (takenSeats < slot.capacity) SlotStatus.FREE else SlotStatus.BOOKED
    }

    private fun takeSeat(slot: TrainingSlotEntity, userId: UUID) {
        if (slot.lifecycle != SlotLifecycle.SCHEDULED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "На это занятие записаться нельзя")
        }
        if (participantRepository.findBySlotIdAndUserId(slotId = slot.id, userId = userId) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Вы уже записаны на это занятие")
        }
        if (seatsTakenIn(slot.id) >= slot.capacity) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Свободных мест не осталось")
        }
        participantRepository.save(
            SlotParticipantEntity(
                id = UUID.randomUUID(),
                slotId = slot.id,
                userId = userId,
                createdAt = Instant.now(clock),
            )
        )
    }

    private fun freeSeat(slot: TrainingSlotEntity, userId: UUID) {
        val participation = participantRepository.findBySlotIdAndUserId(slotId = slot.id, userId = userId) ?: return
        participantRepository.delete(participation)
        notifyWaitlist(slot)
    }

    private fun hasOverlap(
        coachId: UUID,
        startsAt: Instant,
        durationMinutes: Int,
        excludedSlotId: UUID? = null,
    ): Boolean {
        val endsAt = startsAt.plus(durationMinutes.toLong(), ChronoUnit.MINUTES)
        return slotRepository
            .findOverlappingSlotIds(coachId = coachId, startsAt = startsAt, endsAt = endsAt)
            .any { it != excludedSlotId }
    }

    private fun pendingRequestIdsFor(slots: List<TrainingSlotEntity>): Map<UUID, UUID> {
        if (slots.isEmpty()) return emptyMap()
        return changeRequestRepository.findBySlotIdInAndStatus(
            slotIds = slots.map { it.id },
            status = SlotChangeStatus.PENDING,
        ).associate { it.slotId to it.id }
    }

    private fun coachZone(coach: CoachEntity): ZoneId {
        return runCatching { ZoneId.of(coach.zoneId) }.getOrElse {
            throw ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "У тренера некорректный часовой пояс: ${coach.zoneId}",
            )
        }
    }

    private fun requireCoach(coachUserId: UUID): CoachEntity = coachRepository.findByUserId(coachUserId)
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")

    private fun requireSlotOwnedBy(slot: TrainingSlotEntity, coach: CoachEntity) {
        if (slot.coachId != coach.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Слот другого тренера")
        }
    }

    private fun requireActiveCoachClient(coachId: UUID, userId: UUID) {
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coachId, userId = userId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к расписанию тренера")
        }
    }

    private fun displayNameOf(userId: UUID?): String? {
        return userId?.let { userRepository.findByIdOrNull(it)?.displayName }
    }

    private fun toCoachResponse(
        slot: TrainingSlotEntity,
        pendingRequestId: UUID?,
        participants: List<SlotParticipantResponse> = participantsOf(slot.id),
    ): CoachSlotResponse {
        val soleParticipant = participants.singleOrNull()
        return CoachSlotResponse(
            id = slot.id,
            startsAt = slot.startsAt,
            durationMinutes = slot.durationMinutes,
            status = statusOf(slot = slot, takenSeats = participants.size),
            clientUserId = soleParticipant?.userId,
            clientDisplayName = soleParticipant?.displayName,
            pendingChangeRequestId = pendingRequestId,
            capacity = slot.capacity,
            takenSeats = participants.size,
            participants = participants,
        )
    }

    private fun participantsOf(slots: List<TrainingSlotEntity>): Map<UUID, List<SlotParticipantResponse>> {
        val participants = participantRepository.findBySlotIdIn(slots.map { it.id })
        val names = namesOf(participants.map { it.userId })
        return participants
            .groupBy { it.slotId }
            .mapValues { (_, rows) ->
                rows.map { SlotParticipantResponse(userId = it.userId, displayName = names[it.userId]) }
            }
    }

    private fun seatsTakenIn(slots: List<TrainingSlotEntity>): Map<UUID, Int> = participantRepository
        .findBySlotIdIn(slots.map { it.id })
        .groupingBy { it.slotId }
        .eachCount()

    private fun participantsOf(slotId: UUID): List<SlotParticipantResponse> {
        val participants = participantRepository.findBySlotId(slotId)
        val names = namesOf(participants.map { it.userId })
        return participants.map { SlotParticipantResponse(userId = it.userId, displayName = names[it.userId]) }
    }

    private fun namesOf(userIds: List<UUID>): Map<UUID, String> = userRepository
        .findAllById(userIds.distinct())
        .associate { it.id to it.displayName }

    private fun toClientResponse(
        slot: TrainingSlotEntity,
        isMine: Boolean,
        takenSeats: Int,
        pendingBySlot: Map<UUID, UUID>,
        cancellationWindowHours: Int,
        isOnWaitlist: Boolean,
    ): ClientSlotResponse {
        return ClientSlotResponse(
            id = slot.id,
            startsAt = slot.startsAt,
            durationMinutes = slot.durationMinutes,
            isBookedByMe = isMine,
            isAvailable = statusOf(slot = slot, takenSeats = takenSeats) == SlotStatus.FREE,
            pendingChangeRequestId = if (isMine) pendingBySlot[slot.id] else null,
            canRequestChange = isMine && isWithinChangeWindow(
                slot = slot,
                cancellationWindowHours = cancellationWindowHours,
            ),
            isOnWaitlist = isOnWaitlist,
            capacity = slot.capacity,
            takenSeats = takenSeats,
        )
    }

    private fun isWithinChangeWindow(slot: TrainingSlotEntity, cancellationWindowHours: Int): Boolean {
        val deadline = slot.startsAt.minus(cancellationWindowHours.toLong(), ChronoUnit.HOURS)
        return Instant.now(clock).isBefore(deadline)
    }

    private fun toResponse(
        request: SlotChangeRequestEntity,
        slot: TrainingSlotEntity,
    ): SlotChangeRequestResponse = SlotChangeRequestResponse(
        id = request.id,
        slotId = request.slotId,
        slotStartsAt = slot.startsAt,
        requestedByUserId = request.requestedByUserId,
        requestedByDisplayName = displayNameOf(request.requestedByUserId),
        kind = request.kind,
        proposedStartsAt = request.proposedStartsAt,
        status = request.status,
        createdAt = request.createdAt,
    )

    private fun slotNotFound(): Nothing {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Слот не найден")
    }
}
