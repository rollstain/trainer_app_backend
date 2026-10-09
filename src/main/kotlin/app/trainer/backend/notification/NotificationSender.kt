package app.trainer.backend.notification

import app.trainer.backend.coach.CoachQuietHoursLookup
import app.trainer.backend.push.NotificationReason
import app.trainer.backend.push.PushDelivery
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

@Service
class NotificationSender(
    private val delivery: PushDelivery,
    private val notificationRepository: NotificationRepository,
    private val settingRepository: NotificationSettingRepository,
    private val quietHours: CoachQuietHoursLookup,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : PushSender {

    override fun send(userIds: Collection<UUID>, message: PushMessage) {
        val recipients = userIds.distinct()
        if (recipients.isEmpty()) return
        val now = Instant.now(clock)
        if (message.text.collapsesIntoDigest) {
            keep(recipients = recipients, message = message, now = now, heldUntil = emptyMap())
            return
        }
        val muted = mutedOf(recipients = recipients, message = message)
        val quietUntil = quietUntilOf(recipients = recipients, message = message, now = now)
        if (message.text.keptInHistory) {
            val heldUntil = quietUntil.filterKeys { it !in muted }
            keep(recipients = recipients, message = message, now = now, heldUntil = heldUntil)
        }
        val pushed = recipients.filterNot { it in muted || it in quietUntil }
        if (pushed.isNotEmpty()) delivery.send(userIds = pushed, message = message)
    }

    private fun mutedOf(recipients: List<UUID>, message: PushMessage): Set<UUID> {
        val reason = message.text.reason ?: return emptySet()
        return settingRepository
            .findByUserIdInAndReasonAndPushEnabledFalse(userIds = recipients, reason = reason)
            .map { it.userId }
            .toSet()
    }

    private fun quietUntilOf(recipients: List<UUID>, message: PushMessage, now: Instant): Map<UUID, Instant> {
        if (message.text.reason == NotificationReason.CHANGE_REQUESTS) return emptyMap()
        val quietUntil = mutableMapOf<UUID, Instant>()
        for ((userId, window) in quietHours.windowsOf(recipients)) {
            val endsAt = window.endAfter(now) ?: continue
            quietUntil[userId] = endsAt
        }
        return quietUntil
    }

    private fun keep(recipients: List<UUID>, message: PushMessage, now: Instant, heldUntil: Map<UUID, Instant>) {
        val args = objectMapper.writeValueAsString(message.args)
        val data = objectMapper.writeValueAsString(message.data)
        notificationRepository.saveAll(
            recipients.map { userId ->
                NotificationEntity(
                    id = UUID.randomUUID(),
                    userId = userId,
                    kind = message.text,
                    args = args,
                    data = data,
                    createdAt = now,
                    readAt = null,
                    heldUntil = heldUntil[userId],
                )
            }
        )
    }
}
