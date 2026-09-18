package app.trainer.backend.schedule

import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
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
    private val pushSender: PushSender,
    private val clock: Clock,
) {

    fun free(slot: TrainingSlotEntity, userId: UUID) {
        val participation = participantRepository.findBySlotIdAndUserId(slotId = slot.id, userId = userId) ?: return
        participantRepository.delete(participation)
        notifyWaitlist(slot)
    }

    fun timeLabelOf(slot: TrainingSlotEntity): String {
        val coach = coachRepository.findByIdOrNull(slot.coachId)
        val zone = coach?.zoneId?.let { zoneId -> runCatching { ZoneId.of(zoneId) }.getOrNull() } ?: ZoneOffset.UTC
        return slot.startsAt.atZone(zone).format(SLOT_TIME_FORMAT)
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
