package app.trainer.backend.schedule

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
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
private const val MISSED_SESSIONS_WINDOW_DAYS = 30L

@Service
class ScheduleService(
    private val slotRepository: TrainingSlotRepository,
    private val changeRequestRepository: SlotChangeRequestRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val waitlistRepository: SlotWaitlistRepository,
    private val roster: SlotRoster,
    private val participantRepository: SlotParticipantRepository,
    private val seats: SlotSeats,
    private val pushSender: PushSender,
    private val clock: Clock,
) {

    @Transactional
    fun createSlot(coachUserId: UUID, request: CreateSlotRequest): CoachSlotResponse {
        val coach = coachRepository.requireCoach(coachUserId)
        val overlaps = slotRepository.hasOverlap(
            coachId = coach.id,
            startsAt = request.startsAt,
            durationMinutes = request.durationMinutes,
        )
        if (overlaps) {
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
        val coach = coachRepository.requireCoach(coachUserId)
        val zone = coachZone(coach)
        val created = mutableListOf<CoachSlotResponse>()
        val skipped = mutableListOf<SkippedSlotResponse>()

        seriesStarts(request = request, zone = zone).forEach { startsAt ->
            val overlaps = slotRepository.hasOverlap(
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
    fun missedSessionsByClient(coachUserId: UUID, clientUserIds: List<UUID>): Map<UUID, Int> {
        val coach = coachRepository.requireCoach(coachUserId)
        if (clientUserIds.isEmpty()) return emptyMap()
        val now = Instant.now(clock)
        return participantRepository
            .findPastParticipation(
                coachId = coach.id,
                from = now.minus(MISSED_SESSIONS_WINDOW_DAYS, ChronoUnit.DAYS),
                to = now,
                clientIds = clientUserIds.toTypedArray(),
            )
            .groupBy { it.getClientUserId() }
            .mapValues { (_, participation) -> countMissedInARow(participation) }
            .filterValues { it > 0 }
    }

    private fun countMissedInARow(participation: List<PastParticipation>): Int =
        participation
            .filter { it.getStatus() != SlotLifecycle.CANCELLED.name }
            .takeWhile { it.getStatus() != SlotLifecycle.COMPLETED.name }
            .size

    @Transactional(readOnly = true)
    fun coachSchedule(coachUserId: UUID, from: Instant, to: Instant): CoachScheduleResponse {
        val coach = coachRepository.requireCoach(coachUserId)
        val slots = slotRepository.findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(
            coachId = coach.id,
            from = from,
            to = to,
        )
        val pendingBySlot = pendingRequestIdsFor(slots)
        val participantsBySlot = roster.participantsOf(slots)
        val waitlistBySlot = roster.waitlistOf(slots)
        return CoachScheduleResponse(
            coachId = coach.id,
            zoneId = coach.zoneId,
            slots = slots.map { slot ->
                toCoachResponse(
                    slot = slot,
                    pendingRequestId = pendingBySlot[slot.id],
                    participants = participantsBySlot[slot.id].orEmpty(),
                    waitlist = waitlistBySlot[slot.id].orEmpty(),
                )
            },
        )
    }

    @Transactional(readOnly = true)
    fun coachSlot(coachUserId: UUID, slotId: UUID): CoachSlotResponse {
        val coach = coachRepository.requireCoach(coachUserId)
        val slot = slotRepository.findByIdOrNull(slotId) ?: slotNotFound()
        requireSlotOwnedBy(slot = slot, coach = coach)
        return toCoachResponse(slot = slot, pendingRequestId = pendingRequestIdsFor(listOf(slot))[slot.id])
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
        val latestRequests = latestRequestsOf(userId = userId, slots = slots)
        val seatsBySlot = seatsTakenIn(slots)
        val mySlotIds = participantRepository
            .findBySlotIdIn(slots.map { it.id })
            .filter { it.userId == userId }
            .map { it.slotId }
            .toSet()
        val waitlistPositions = roster.waitlistPositionsOf(userId = userId, slotIds = slots.map { it.id })
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
                        latestRequest = latestRequests[slot.id],
                        cancellationWindowHours = coach.cancellationWindowHours,
                        waitlistPosition = waitlistPositions[slot.id],
                    )
                },
        )
    }

    @Transactional
    fun assignSlot(coachUserId: UUID, slotId: UUID, clientUserId: UUID): CoachSlotResponse {
        val coach = coachRepository.requireCoach(coachUserId)
        val slot = slotRepository.findWithLockById(slotId) ?: slotNotFound()
        requireSlotOwnedBy(slot = slot, coach = coach)
        requireActiveCoachClient(coachId = coach.id, userId = clientUserId)
        if (slot.lifecycle == SlotLifecycle.COMPLETED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Тренировка уже проведена")
        }
        takeSeat(slot = slot, userId = clientUserId)
        if (slot.startsAt.isAfter(Instant.now(clock))) notifyAssignment(slot = slot, clientUserId = clientUserId)
        return toCoachResponse(slot = slot, pendingRequestId = null)
    }

    private fun notifyAssignment(slot: TrainingSlotEntity, clientUserId: UUID) {
        pushSender.send(
            userIds = listOf(clientUserId),
            message = PushMessage(
                channel = PushChannel.SCHEDULE,
                text = PushText.SLOT_ASSIGNED,
                args = listOf(seats.timeLabelOf(slot)),
                data = mapOf(PUSH_SLOT_ID_KEY to slot.id.toString()),
            ),
        )
    }

    @Transactional
    fun removeParticipant(coachUserId: UUID, slotId: UUID, clientUserId: UUID): CoachSlotResponse {
        val coach = coachRepository.requireCoach(coachUserId)
        val slot = slotRepository.findWithLockById(slotId) ?: slotNotFound()
        requireSlotOwnedBy(slot = slot, coach = coach)
        if (slot.lifecycle == SlotLifecycle.COMPLETED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Тренировка уже проведена")
        }
        if (participantRepository.findBySlotIdAndUserId(slotId = slot.id, userId = clientUserId) == null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Подопечный не записан на это занятие")
        }
        rejectPendingRequest(slotId = slot.id)
        seats.free(slot = slot, userId = clientUserId)
        return toCoachResponse(slot = slot, pendingRequestId = null)
    }

    @Transactional
    fun cancelSlot(coachUserId: UUID, slotId: UUID): CoachSlotResponse {
        val coach = coachRepository.requireCoach(coachUserId)
        val slot = slotRepository.findWithLockById(slotId) ?: slotNotFound()
        requireSlotOwnedBy(slot = slot, coach = coach)
        val participantsExpectIt =
            slot.lifecycle == SlotLifecycle.SCHEDULED && slot.startsAt.isAfter(Instant.now(clock))
        slot.lifecycle = SlotLifecycle.CANCELLED
        rejectPendingRequest(slotId = slot.id)
        if (participantsExpectIt) notifyCancellation(slot)
        return toCoachResponse(slot = slot, pendingRequestId = null)
    }

    private fun notifyCancellation(slot: TrainingSlotEntity) {
        val participants = participantRepository.findBySlotId(slot.id)
        if (participants.isEmpty()) return
        pushSender.send(
            userIds = participants.map { it.userId },
            message = PushMessage(
                channel = PushChannel.SCHEDULE,
                text = PushText.SLOT_CANCELLED,
                args = listOf(seats.timeLabelOf(slot)),
                data = mapOf(PUSH_SLOT_ID_KEY to slot.id.toString()),
            ),
        )
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
                seats.free(slot = slot, userId = clientUserId)
            }
    }

    @Transactional
    fun completeSlot(coachUserId: UUID, slotId: UUID): CoachSlotResponse {
        val coach = coachRepository.requireCoach(coachUserId)
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
        if (!startsInFuture(slot)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Это занятие уже прошло")
        }
        takeSeat(slot = slot, userId = userId)
        val entry = waitlistRepository.findBySlotIdAndUserId(slotId = slot.id, userId = userId)
        if (entry != null) waitlistRepository.delete(entry)
        return clientResponseOf(slot = slot, userId = userId)
    }

    @Transactional
    fun joinWaitlist(userId: UUID, slotId: UUID): ClientSlotResponse {
        val slot = slotRepository.findByIdOrNull(slotId) ?: slotNotFound()
        requireActiveCoachClient(coachId = slot.coachId, userId = userId)
        if (!startsInFuture(slot)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Это занятие уже прошло")
        }
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
        return clientResponseOf(slot = slot, userId = userId)
    }

    @Transactional
    fun leaveWaitlist(userId: UUID, slotId: UUID): ClientSlotResponse {
        val slot = slotRepository.findByIdOrNull(slotId) ?: slotNotFound()
        val entry = waitlistRepository.findBySlotIdAndUserId(slotId = slotId, userId = userId)
        if (entry != null) waitlistRepository.delete(entry)
        return clientResponseOf(slot = slot, userId = userId)
    }

    private fun clientResponseOf(slot: TrainingSlotEntity, userId: UUID): ClientSlotResponse {
        val coach = coachRepository.findByIdOrNull(slot.coachId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Тренер не найден")
        return toClientResponse(
            slot = slot,
            isMine = participantRepository.findBySlotIdAndUserId(slotId = slot.id, userId = userId) != null,
            takenSeats = seatsTakenIn(slot.id),
            pendingBySlot = pendingRequestIdsFor(listOf(slot)),
            latestRequest = latestRequestsOf(userId = userId, slots = listOf(slot))[slot.id],
            cancellationWindowHours = coach.cancellationWindowHours,
            waitlistPosition = roster.waitlistPositionsOf(userId = userId, slotIds = listOf(slot.id))[slot.id],
        )
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

    private fun startsInFuture(slot: TrainingSlotEntity): Boolean = slot.startsAt.isAfter(Instant.now(clock))

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

    private fun latestRequestsOf(
        userId: UUID,
        slots: List<TrainingSlotEntity>,
    ): Map<UUID, SlotChangeRequestEntity> {
        if (slots.isEmpty()) return emptyMap()
        return changeRequestRepository
            .findBySlotIdInAndRequestedByUserId(slotIds = slots.map { it.id }, requestedByUserId = userId)
            .groupBy { it.slotId }
            .mapValues { (_, requests) -> requests.maxBy { it.createdAt } }
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

    private fun requireActiveCoachClient(coachId: UUID, userId: UUID) {
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coachId, userId = userId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к расписанию тренера")
        }
    }

    private fun toCoachResponse(
        slot: TrainingSlotEntity,
        pendingRequestId: UUID?,
        participants: List<SlotParticipantResponse> = roster.participantsOf(slot),
        waitlist: List<SlotWaitlistResponse> = roster.waitlistOf(slot.id),
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
            waitlist = waitlist,
        )
    }

    private fun seatsTakenIn(slots: List<TrainingSlotEntity>): Map<UUID, Int> = participantRepository
        .findBySlotIdIn(slots.map { it.id })
        .groupingBy { it.slotId }
        .eachCount()

    private fun toClientResponse(
        slot: TrainingSlotEntity,
        isMine: Boolean,
        takenSeats: Int,
        pendingBySlot: Map<UUID, UUID>,
        latestRequest: SlotChangeRequestEntity?,
        cancellationWindowHours: Int,
        waitlistPosition: Int?,
    ): ClientSlotResponse {
        return ClientSlotResponse(
            id = slot.id,
            startsAt = slot.startsAt,
            durationMinutes = slot.durationMinutes,
            isBookedByMe = isMine,
            isAvailable = statusOf(slot = slot, takenSeats = takenSeats) == SlotStatus.FREE &&
                startsInFuture(slot),
            pendingChangeRequestId = if (isMine) pendingBySlot[slot.id] else null,
            changeRequest = if (isMine) latestRequest?.let(::toClientChangeResponse) else null,
            canRequestChange = isMine && isBeforeChangeDeadline(
                startsAt = slot.startsAt,
                cancellationWindowHours = cancellationWindowHours,
                now = Instant.now(clock),
            ),
            isOnWaitlist = waitlistPosition != null,
            waitlistPosition = waitlistPosition,
            capacity = slot.capacity,
            takenSeats = takenSeats,
        )
    }

    private fun toClientChangeResponse(request: SlotChangeRequestEntity): ClientChangeRequestResponse =
        ClientChangeRequestResponse(
            id = request.id,
            kind = request.kind,
            status = request.status,
            proposedStartsAt = request.proposedStartsAt,
            originalStartsAt = request.originalStartsAt,
            coachComment = request.coachComment,
            resolvedAt = request.resolvedAt,
        )
}
