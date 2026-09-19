package app.trainer.backend.push

import java.util.UUID

enum class PushChannel(val androidChannelId: String) {
    CHAT("chat_messages"),
    SCHEDULE("schedule"),
}

enum class NotificationReason { COACH_REPLIES, SESSION_REMINDERS, SCHEDULE_CHANGES, NEW_PROGRAMS }

enum class PushText(
    val titleKey: String,
    val bodyKey: String,
    val keptInHistory: Boolean,
    val reason: NotificationReason?,
) {
    NEW_CHAT_MESSAGE(
        "push.chat.new-message.title",
        "push.chat.new-message.body",
        keptInHistory = false,
        reason = null,
    ),
    WAITLIST_SLOT_FREED(
        "push.schedule.waitlist.title",
        "push.schedule.waitlist.body",
        keptInHistory = true,
        reason = NotificationReason.SCHEDULE_CHANGES,
    ),
    SLOT_CANCELLED(
        "push.schedule.cancelled.title",
        "push.schedule.cancelled.body",
        keptInHistory = true,
        reason = NotificationReason.SCHEDULE_CHANGES,
    ),
    CLIENT_CANCELLED(
        "push.schedule.client-cancelled.title",
        "push.schedule.client-cancelled.body",
        keptInHistory = true,
        reason = NotificationReason.SCHEDULE_CHANGES,
    ),
    SESSION_SOON(
        "push.reminder.session.title",
        "push.reminder.session.body",
        keptInHistory = true,
        reason = NotificationReason.SESSION_REMINDERS,
    ),
    DIARY_IDLE(
        "push.reminder.diary.title",
        "push.reminder.diary.body",
        keptInHistory = true,
        reason = null,
    ),
    CHECK_IN_IDLE(
        "push.reminder.check-in.title",
        "push.reminder.check-in.body",
        keptInHistory = true,
        reason = null,
    ),
}

data class PushMessage(
    val channel: PushChannel,
    val text: PushText,
    val args: List<String>,
    val data: Map<String, String>,
)

interface PushSender {

    fun send(userIds: Collection<UUID>, message: PushMessage)
}

interface PushDelivery {

    fun send(userIds: Collection<UUID>, message: PushMessage)
}
