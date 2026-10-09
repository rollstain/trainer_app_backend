package app.trainer.backend.notification

import app.trainer.backend.coach.CoachQuietHoursLookup
import app.trainer.backend.coach.QuietSpan
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
        val quiet = quietSpansOf(recipients = recipients, now = now)
        if (message.text.collapsesIntoDigest) {
            keep(recipients = recipients, message = message, now = now, quiet = quiet, held = emptySet())
            return
        }
        val muted = mutedOf(recipients = recipients, message = message)
        val silenced = if (message.text.reason == NotificationReason.CHANGE_REQUESTS) emptySet() else quiet.keys
        if (message.text.keptInHistory) {
            keep(recipients = recipients, message = message, now = now, quiet = quiet, held = silenced - muted)
        }
        val pushed = recipients.filterNot { it in muted || it in silenced }
        if (pushed.isNotEmpty()) delivery.send(userIds = pushed, message = message)
    }

    private fun mutedOf(recipients: List<UUID>, message: PushMessage): Set<UUID> {
        val reason = message.text.reason ?: return emptySet()
        return settingRepository
            .findByUserIdInAndReasonAndPushEnabledFalse(userIds = recipients, reason = reason)
            .map { it.userId }
            .toSet()
    }

    private fun quietSpansOf(recipients: List<UUID>, now: Instant): Map<UUID, QuietSpan> {
        val spans = mutableMapOf<UUID, QuietSpan>()
        for ((userId, window) in quietHours.windowsOf(recipients)) {
            val span = window.spanAt(now) ?: continue
            spans[userId] = span
        }
        return spans
    }

    private fun keep(
        recipients: List<UUID>,
        message: PushMessage,
        now: Instant,
        quiet: Map<UUID, QuietSpan>,
        held: Set<UUID>,
    ) {
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
                    heldUntil = if (userId in held) quiet[userId]?.until else null,
                    quietFrom = quiet[userId]?.from,
                    quietUntil = quiet[userId]?.until,
                )
            }
        )
    }
}
