package app.trainer.backend.push

import java.util.UUID

enum class PushChannel(val androidChannelId: String) {
    CHAT("chat_messages"),
    SCHEDULE("schedule"),
}

enum class NotificationAudience { CLIENT, COACH }

enum class NotificationDelivery { IMMEDIATE, DAILY_DIGEST }

enum class NotificationReason(
    val audience: NotificationAudience,
    val canTurnOff: Boolean = true,
    val delivery: NotificationDelivery = NotificationDelivery.IMMEDIATE,
) {
    COACH_REPLIES(NotificationAudience.CLIENT),
    SESSION_REMINDERS(NotificationAudience.CLIENT),
    SCHEDULE_CHANGES(NotificationAudience.CLIENT),
    NEW_PROGRAMS(NotificationAudience.CLIENT),
    CHANGE_REQUESTS(NotificationAudience.COACH, canTurnOff = false),
    NEW_CHECK_INS(NotificationAudience.COACH, delivery = NotificationDelivery.DAILY_DIGEST),
    NEW_CLIENTS(NotificationAudience.COACH),
    SLOT_BOOKINGS(NotificationAudience.COACH),
}

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
        reason = NotificationReason.CHANGE_REQUESTS,
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
        reason = NotificationReason.CHANGE_REQUESTS,
    ),
    CANCEL_REQUESTED(
        "push.schedule.cancel-requested.title",
        "push.schedule.cancel-requested.body",
        keptInHistory = true,
        reason = NotificationReason.CHANGE_REQUESTS,
    ),
    NEW_CHECK_IN(
        "push.coach.check-in.title",
        "push.coach.check-in.body",
        keptInHistory = true,
        reason = NotificationReason.NEW_CHECK_INS,
    ),
    NEW_FORM_CHECK(
        "push.coach.form-check.title",
        "push.coach.form-check.body",
        keptInHistory = true,
        reason = NotificationReason.NEW_CHECK_INS,
    ),
    NEW_CLIENT(
        "push.coach.new-client.title",
        "push.coach.new-client.body",
        keptInHistory = true,
        reason = NotificationReason.NEW_CLIENTS,
    ),
    SLOT_BOOKED(
        "push.coach.slot-booked.title",
        "push.coach.slot-booked.body",
        keptInHistory = true,
        reason = NotificationReason.SLOT_BOOKINGS,
    ),
    CHECK_INS_WAITING(
        "push.coach.check-ins-waiting.title",
        "push.coach.check-ins-waiting.body",
        keptInHistory = false,
        reason = NotificationReason.NEW_CHECK_INS,
    ),
    FORM_CHECKS_WAITING(
        "push.coach.form-checks-waiting.title",
        "push.coach.form-checks-waiting.body",
        keptInHistory = false,
        reason = NotificationReason.NEW_CHECK_INS,
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
    COACH_REQUEST_APPROVED(
        "push.coach-request.approved.title",
        "push.coach-request.approved.body",
        keptInHistory = true,
        reason = null,
    ),
    COACH_REQUEST_DECLINED(
        "push.coach-request.declined.title",
        "push.coach-request.declined.body",
        keptInHistory = true,
        reason = null,
    ),
    PROGRAM_ENDED(
        "push.program.ended.title",
        "push.program.ended.body",
        keptInHistory = true,
        reason = NotificationReason.NEW_PROGRAMS,
    ),
    ;

    val collapsesIntoDigest: Boolean
        get() = keptInHistory && reason?.delivery == NotificationDelivery.DAILY_DIGEST
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
