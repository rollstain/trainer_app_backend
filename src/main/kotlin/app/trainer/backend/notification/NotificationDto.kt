package app.trainer.backend.notification

import app.trainer.backend.push.NotificationReason
import app.trainer.backend.push.PushText
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val kind: PushText,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val createdAt: Instant,
    val isRead: Boolean,
)

data class UnreadNotificationsResponse(
    val count: Long,
)

data class NotificationSettingResponse(
    val reason: NotificationReason,
    val pushEnabled: Boolean,
)

data class UpdateNotificationSettingRequest(
    val pushEnabled: Boolean,
)
