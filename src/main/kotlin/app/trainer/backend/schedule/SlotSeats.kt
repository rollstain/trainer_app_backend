package app.trainer.backend.schedule

import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

private const val PUSH_SLOT_ID_KEY = "slotId"
private val SLOT_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")

@Component
class SlotSeats(
    private val participantRepository: SlotParticipantRepository,
    private val waitlistRepository: SlotWaitlistRepository,
    private val coachRepository: CoachRepository,
    private val userRepository: UserRepository,
    private val pushSender: PushSender,
    private val clock: Clock,
) {

    fun free(slot: TrainingSlotEntity, userId: UUID) {
        val participation = participantRepository.findBySlotIdAndUserId(slotId = slot.id, userId = userId) ?: return
        participantRepository.delete(participation)
        notifyWaitlist(slot)
    }

    fun notifyCoachOfBooking(slot: TrainingSlotEntity, clientUserId: UUID) {
        val coach = coachRepository.findByIdOrNull(slot.coachId) ?: return
        val clientName = userRepository.findByIdOrNull(clientUserId)?.displayName ?: return
        pushSender.send(
            userIds = listOf(coach.userId),
            message = PushMessage(
                channel = PushChannel.SCHEDULE,
                text = PushText.SLOT_BOOKED,
                args = listOf(clientName, timeLabelOf(slot)),
                data = mapOf(PUSH_SLOT_ID_KEY to slot.id.toString()),
            ),
        )
    }

    fun timeLabelOf(slot: TrainingSlotEntity): String = timeLabelAt(coachId = slot.coachId, startsAt = slot.startsAt)

    fun timeLabelAt(coachId: UUID, startsAt: Instant): String {
        val coach = coachRepository.findByIdOrNull(coachId)
        val zone = coach?.zoneId?.let { zoneId -> runCatching { ZoneId.of(zoneId) }.getOrNull() } ?: ZoneOffset.UTC
        return startsAt.atZone(zone).format(SLOT_TIME_FORMAT)
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
                args = listOf(timeLabelOf(slot)),
                data = mapOf(PUSH_SLOT_ID_KEY to slot.id.toString()),
            ),
        )
    }
}
