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
    SLOT_ASSIGNED(
        "push.schedule.assigned.title",
        "push.schedule.assigned.body",
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
    CHECK_IN_REVIEWED(
        "push.check-in.reviewed.title",
        "push.check-in.reviewed.body",
        keptInHistory = true,
        reason = NotificationReason.COACH_REPLIES,
    ),
    FORM_CHECK_REVIEWED(
        "push.form-check.reviewed.title",
        "push.form-check.reviewed.body",
        keptInHistory = true,
        reason = NotificationReason.COACH_REPLIES,
    ),
    RESCHEDULE_REQUESTED(
        "push.schedule.reschedule-requested.title",
        "push.schedule.reschedule-requested.body",
        keptInHistory = true,
        reason = NotificationReason.SCHEDULE_CHANGES,
    ),
    CANCEL_REQUESTED(
        "push.schedule.cancel-requested.title",
        "push.schedule.cancel-requested.body",
        keptInHistory = true,
        reason = NotificationReason.SCHEDULE_CHANGES,
    ),
    RESCHEDULE_APPROVED(
        "push.schedule.reschedule-approved.title",
        "push.schedule.reschedule-approved.body",
        keptInHistory = true,
        reason = NotificationReason.SCHEDULE_CHANGES,
    ),
    RESCHEDULE_DECLINED(
        "push.schedule.reschedule-declined.title",
        "push.schedule.reschedule-declined.body",
        keptInHistory = true,
        reason = NotificationReason.SCHEDULE_CHANGES,
    ),
    CANCEL_APPROVED(
        "push.schedule.cancel-approved.title",
        "push.schedule.cancel-approved.body",
        keptInHistory = true,
        reason = NotificationReason.SCHEDULE_CHANGES,
    ),
    CANCEL_DECLINED(
        "push.schedule.cancel-declined.title",
        "push.schedule.cancel-declined.body",
        keptInHistory = true,
        reason = NotificationReason.SCHEDULE_CHANGES,
    ),
    PROGRAM_ASSIGNED(
        "push.program.assigned.title",
        "push.program.assigned.body",
        keptInHistory = true,
        reason = NotificationReason.NEW_PROGRAMS,
    ),
    PROGRAM_ENDED(
        "push.program.ended.title",
        "push.program.ended.body",
        keptInHistory = true,
        reason = NotificationReason.NEW_PROGRAMS,
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
