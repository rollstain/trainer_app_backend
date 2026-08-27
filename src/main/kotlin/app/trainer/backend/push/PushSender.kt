package app.trainer.backend.push

import java.util.UUID

enum class PushChannel(val androidChannelId: String) {
    CHAT("chat_messages"),
    SCHEDULE("schedule"),
}

enum class PushText(val titleKey: String, val bodyKey: String) {
    NEW_CHAT_MESSAGE("push.chat.new-message.title", "push.chat.new-message.body"),
    WAITLIST_SLOT_FREED("push.schedule.waitlist.title", "push.schedule.waitlist.body"),
    SESSION_SOON("push.reminder.session.title", "push.reminder.session.body"),
    DIARY_IDLE("push.reminder.diary.title", "push.reminder.diary.body"),
    CHECK_IN_IDLE("push.reminder.check-in.title", "push.reminder.check-in.body"),
}

data class PushMessage(
    val channel: PushChannel,
    val text: PushText,
    val data: Map<String, String>,
)

interface PushSender {

    fun send(userIds: Collection<UUID>, message: PushMessage)
}
