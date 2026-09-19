package app.trainer.backend.notification

import app.trainer.backend.push.PushDelivery
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class NotificationSender(
    private val delivery: PushDelivery,
    private val notificationRepository: NotificationRepository,
    private val settingRepository: NotificationSettingRepository,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) : PushSender {

    override fun send(userIds: Collection<UUID>, message: PushMessage) {
        val recipients = userIds.distinct()
        if (recipients.isEmpty()) return
        if (message.text.keptInHistory) keep(recipients = recipients, message = message)
        val muted = message.text.reason
            ?.let { reason ->
                settingRepository
                    .findByUserIdInAndReasonAndPushEnabledFalse(userIds = recipients, reason = reason)
                    .map { it.userId }
                    .toSet()
            }
            .orEmpty()
        val pushed = recipients.filterNot { it in muted }
        if (pushed.isNotEmpty()) delivery.send(userIds = pushed, message = message)
    }

    private fun keep(recipients: List<UUID>, message: PushMessage) {
        val now = Instant.now(clock)
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
                )
            }
        )
    }
}
