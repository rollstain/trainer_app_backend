package app.trainer.backend.schedule

import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.config.EXTRA_ROW_TO_DETECT_NEXT_PAGE
import app.trainer.backend.config.MAX_PAGE_SIZE
import app.trainer.backend.config.Page
import app.trainer.backend.config.PageCursor
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.config.encodeCursor
import app.trainer.backend.config.pageSizeOf
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val PUSH_SLOT_ID_KEY = "slotId"
private const val PERSONAL_SLOT_CAPACITY = 1
private const val CHANGE_REQUESTS_PER_PAGE = 20

@Service
class SlotChangeService(
    private val slotRepository: TrainingSlotRepository,
    private val changeRequestRepository: SlotChangeRequestRepository,
    private val participantRepository: SlotParticipantRepository,
    private val coachRepository: CoachRepository,
    private val userRepository: UserRepository,
    private val seats: SlotSeats,
    private val pushSender: PushSender,
    private val clock: Clock,
) {

    @Transactional
    fun requestChange(userId: UUID, slotId: UUID, body: SlotChangeRequestBody): SlotChangeRequestResponse {
        val slot = slotRepository.findByIdOrNull(slotId) ?: slotNotFound()
        val now = Instant.now(clock)
        requireChangeable(slot = slot, userId = userId, body = body, now = now)
        val coach = coachRepository.findByIdOrNull(slot.coachId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Тренер не найден")
        val beforeDeadline = isBeforeChangeDeadline(
            startsAt = slot.startsAt,
            cancellationWindowHours = coach.cancellationWindowHours,
            now = now,
        )
        if (body.kind == SlotChangeKind.RESCHEDULE && !beforeDeadline) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Перенести запись можно не позднее чем за ${coach.cancellationWindowHours} ч до начала",
            )
        }
        val alreadyPending = changeRequestRepository.findBySlotIdAndStatus(
            slotId = slotId,
            status = SlotChangeStatus.PENDING,
        )
        if (alreadyPending != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "По слоту уже есть заявка на рассмотрении")
        }
        val cancelsNow = body.kind == SlotChangeKind.CANCEL && beforeDeadline
        val request = changeRequestRepository.save(
            SlotChangeRequestEntity(
                id = UUID.randomUUID(),
                slotId = slotId,
                requestedByUserId = userId,
                kind = body.kind,
                proposedStartsAt = body.proposedStartsAt,
                originalStartsAt = slot.startsAt,
                status = if (cancelsNow) SlotChangeStatus.APPROVED else SlotChangeStatus.PENDING,
                createdAt = now,
                resolvedAt = if (cancelsNow) now else null,
                coachComment = null,
            )
        )
        if (cancelsNow) {
            seats.free(slot = slot, userId = userId)
            notifyCoachOfCancellation(coach = coach, slot = slot, userId = userId)
        }
        return toResponse(request = request, slot = slot)
    }

    @Transactional
    fun withdrawChange(userId: UUID, requestId: UUID) {
        val request = changeRequestRepository.findByIdOrNull(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена")
        if (request.requestedByUserId != userId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваша заявка")
        }
        if (request.status != SlotChangeStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже рассмотрена")
        }
        changeRequestRepository.delete(request)
    }

    @Transactional(readOnly = true)
    fun pendingChangeRequests(
        coachUserId: UUID,
        from: Instant?,
        to: Instant?,
        limit: Int?,
        after: String?,
    ): Page<SlotChangeRequestResponse> {
        val coach = coachRepository.requireCoach(coachUserId)
        val pageSize = if (from == null || to == null) {
            pageSizeOf(limit) ?: CHANGE_REQUESTS_PER_PAGE
        } else {
            MAX_PAGE_SIZE
        }
        val cursor = decodeCursor(after)
        val fetched = changeRequestRepository.findByCoachIdAndStatusPage(
            coachId = coach.id,
            status = SlotChangeStatus.PENDING.name,
            from = from?.toString(),
            to = to?.toString(),
            afterCreatedAt = cursor?.sortKey,
            afterId = cursor?.id,
            pageSize = pageSize + EXTRA_ROW_TO_DETECT_NEXT_PAGE,
        )
        val requests = fetched.take(pageSize)
        val last = requests.lastOrNull()?.takeIf { fetched.size > pageSize }
        val slotsById = slotRepository
            .findAllById(requests.map { it.slotId }.distinct())
            .associateBy { it.id }
        return Page(
            items = requests.mapNotNull { request ->
                val slot = slotsById[request.slotId] ?: return@mapNotNull null
                toResponse(request = request, slot = slot)
            },
            nextCursor = last?.let { encodeCursor(PageCursor(sortKey = it.createdAt.toString(), id = it.id)) },
        )
    }

    @Transactional
    fun resolveChange(
        coachUserId: UUID,
        requestId: UUID,
        approve: Boolean,
        comment: String?,
    ): SlotChangeRequestResponse {
        val coach = coachRepository.requireCoach(coachUserId)
        val request = changeRequestRepository.findByIdOrNull(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена")
        if (request.status != SlotChangeStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже рассмотрена")
        }
        val slot = slotRepository.findWithLockById(request.slotId) ?: slotNotFound()
        requireSlotOwnedBy(slot = slot, coach = coach)

        request.status = if (approve) SlotChangeStatus.APPROVED else SlotChangeStatus.REJECTED
        request.resolvedAt = Instant.now(clock)
        request.coachComment = comment?.trim()?.takeIf { it.isNotEmpty() }
        if (approve) applyChange(slot = slot, request = request)
        return toResponse(request = request, slot = slot)
    }

    private fun requireChangeable(slot: TrainingSlotEntity, userId: UUID, body: SlotChangeRequestBody, now: Instant) {
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
        if (!slot.startsAt.isAfter(now)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Занятие уже началось")
        }
    }

    private fun applyChange(slot: TrainingSlotEntity, request: SlotChangeRequestEntity) {
        when (request.kind) {
            SlotChangeKind.CANCEL -> seats.free(slot = slot, userId = request.requestedByUserId)
            SlotChangeKind.RESCHEDULE -> {
                val proposed = request.proposedStartsAt
                    ?: throw ResponseStatusException(HttpStatus.CONFLICT, "В заявке нет нового времени")
                val overlaps = slotRepository.hasOverlap(
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

    private fun notifyCoachOfCancellation(coach: CoachEntity, slot: TrainingSlotEntity, userId: UUID) {
        val clientName = userRepository.findByIdOrNull(userId)?.displayName ?: return
        pushSender.send(
            userIds = listOf(coach.userId),
            message = PushMessage(
                channel = PushChannel.SCHEDULE,
                text = PushText.CLIENT_CANCELLED,
                args = listOf(clientName, seats.timeLabelOf(slot)),
                data = mapOf(PUSH_SLOT_ID_KEY to slot.id.toString()),
            ),
        )
    }

    private fun toResponse(
        request: SlotChangeRequestEntity,
        slot: TrainingSlotEntity,
    ): SlotChangeRequestResponse = SlotChangeRequestResponse(
        id = request.id,
        slotId = request.slotId,
        slotStartsAt = slot.startsAt,
        requestedByUserId = request.requestedByUserId,
        requestedByDisplayName = userRepository.findByIdOrNull(request.requestedByUserId)?.displayName,
        kind = request.kind,
        proposedStartsAt = request.proposedStartsAt,
        status = request.status,
        createdAt = request.createdAt,
    )
}
