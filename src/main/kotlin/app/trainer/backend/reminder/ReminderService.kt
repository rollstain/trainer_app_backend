package app.trainer.backend.reminder

import app.trainer.backend.checkin.CheckInRepository
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.schedule.SlotLifecycle
import app.trainer.backend.schedule.SlotParticipantRepository
import app.trainer.backend.schedule.TrainingSlotRepository
import app.trainer.backend.traininglog.TrainingLogEntryRepository
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val SESSION_REMINDER_LEAD_MINUTES = 60L
private const val SESSION_REMINDER_WINDOW_MINUTES = 10L
private const val DIARY_IDLE_DAYS = 7L
private const val CHECK_IN_IDLE_DAYS = 14L
private const val ENGAGEMENT_LOOKBACK_DAYS = 120L

private const val PUSH_SLOT_ID_KEY = "slotId"
private const val QUIET_HOURS_FROM = 22
private const val QUIET_HOURS_UNTIL = 8
private const val PERSONAL_SESSION_PARTICIPANTS = 1
private val SESSION_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val CHECK_IN_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

@Service
class ReminderService(
    private val slotRepository: TrainingSlotRepository,
    private val entryRepository: TrainingLogEntryRepository,
    private val checkInRepository: CheckInRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val participantRepository: SlotParticipantRepository,
    private val userRepository: UserRepository,
    private val reminderLogRepository: ReminderLogRepository,
    private val pushSender: PushSender,
    private val clock: Clock,
) {

    @Transactional
    fun remindAboutSessions(): Int {
        val now = Instant.now(clock)
        val from = now.plus(SESSION_REMINDER_LEAD_MINUTES - SESSION_REMINDER_WINDOW_MINUTES, ChronoUnit.MINUTES)
        val to = now.plus(SESSION_REMINDER_LEAD_MINUTES, ChronoUnit.MINUTES)
        val upcoming = slotRepository
            .findByStartsAtBetweenOrderByStartsAtAsc(from, to)
            .filter { it.lifecycle == SlotLifecycle.SCHEDULED }
        if (upcoming.isEmpty()) return 0

        val remindingCoaches = coachRepository
            .findAllById(upcoming.map { it.coachId }.distinct())
            .filter { it.sessionRemindersEnabled }
            .associateBy { it.id }
        val reminded = upcoming.filter { slot ->
            val coach = remindingCoaches[slot.coachId] ?: return@filter false
            !isQuietHourFor(coach = coach, startsAt = slot.startsAt)
        }
        if (reminded.isEmpty()) return 0
        val slotsById = reminded.associateBy { it.id }
        val participation = participantRepository.findBySlotIdIn(reminded.map { it.id })
        val coachUserIds = remindingCoaches.values.map { it.userId }
        val namesByUserId = userRepository
            .findAllById((participation.map { it.userId } + coachUserIds).distinct())
            .associate { it.id to it.displayName }
        val participantsBySlot = participation.groupBy { it.slotId }
        return participation.count { participant ->
            val slot = slotsById[participant.slotId] ?: return@count false
            val coach = remindingCoaches[slot.coachId] ?: return@count false
            val zone = zoneOf(coach) ?: return@count false
            send(
                userId = participant.userId,
                kind = ReminderKind.SESSION,
                subject = participant.slotId.toString(),
                message = PushMessage(
                    channel = PushChannel.SCHEDULE,
                    text = PushText.SESSION_SOON,
                    args = sessionArgsOf(
                        startsAt = slot.startsAt,
                        zone = zone,
                        coachUserId = coach.userId,
                        recipientUserId = participant.userId,
                        participants = participantsBySlot[participant.slotId].orEmpty().map { it.userId },
                        namesByUserId = namesByUserId,
                    ),
                    data = mapOf(PUSH_SLOT_ID_KEY to participant.slotId.toString()),
                ),
            )
        }
    }

    private fun sessionArgsOf(
        startsAt: Instant,
        zone: ZoneId,
        coachUserId: UUID,
        recipientUserId: UUID,
        participants: List<UUID>,
        namesByUserId: Map<UUID, String>,
    ): List<String> {
        val timeLabel = startsAt.atZone(zone).format(SESSION_TIME_FORMAT)
        val companion = when {
            participants.size > PERSONAL_SESSION_PARTICIPANTS -> participants.size.toString()
            recipientUserId == coachUserId ->
                participants.firstOrNull { it != coachUserId }?.let { namesByUserId[it] }.orEmpty()
            else -> namesByUserId[coachUserId].orEmpty()
        }
        return listOf(timeLabel, companion)
    }

    private fun zoneOf(coach: CoachEntity): ZoneId? =
        runCatching { ZoneId.of(coach.zoneId) }.getOrNull()

    private fun isQuietHourFor(coach: CoachEntity, startsAt: Instant): Boolean {
        val zone = zoneOf(coach) ?: return false
        val hour = startsAt.minus(SESSION_REMINDER_LEAD_MINUTES, ChronoUnit.MINUTES).atZone(zone).hour
        return hour >= QUIET_HOURS_FROM || hour < QUIET_HOURS_UNTIL
    }

    @Transactional
    fun remindAboutEngagement(): Int {
        val now = Instant.now(clock)
        var sent = 0
        coachRepository.findAll().forEach { coach ->
            val zone = engagementZoneOf(coach = coach, now = now) ?: return@forEach
            val today = now.atZone(zone).toLocalDate()
            val week = weekBucketOf(today)
            coachClientRepository
                .findByCoachIdAndStatus(coachId = coach.id, status = CoachClientStatus.ACTIVE)
                .forEach { link ->
                    val diaryNudged = coach.diaryRemindersEnabled &&
                        remindIdleDiary(clientUserId = link.userId, today = today, week = week)
                    if (diaryNudged) sent++
                    val checkInNudged = coach.checkInRemindersEnabled &&
                        remindIdleCheckIn(clientUserId = link.userId, today = today, week = week)
                    if (checkInNudged) sent++
                }
        }
        return sent
    }

    private fun engagementZoneOf(coach: CoachEntity, now: Instant): ZoneId? {
        if (!coach.diaryRemindersEnabled && !coach.checkInRemindersEnabled) return null
        val zone = runCatching { ZoneId.of(coach.zoneId) }.getOrNull() ?: return null
        return zone.takeIf { now.atZone(it).hour == coach.reminderHour }
    }

    private fun remindIdleDiary(clientUserId: UUID, today: LocalDate, week: String): Boolean {
        val lastEntry = entryRepository
            .findByClientUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                clientUserId = clientUserId,
                from = today.minusDays(ENGAGEMENT_LOOKBACK_DAYS),
                to = today,
            )
            .firstOrNull()
            ?.entryDate
        if (lastEntry != null && ChronoUnit.DAYS.between(lastEntry, today) < DIARY_IDLE_DAYS) return false
        return send(
            userId = clientUserId,
            kind = ReminderKind.DIARY_IDLE,
            subject = week,
            message = PushMessage(
                channel = PushChannel.SCHEDULE,
                text = PushText.DIARY_IDLE,
                args = listOf(daysSinceLabelOf(lastEntry = lastEntry, today = today)),
                data = emptyMap(),
            ),
        )
    }

    private fun remindIdleCheckIn(clientUserId: UUID, today: LocalDate, week: String): Boolean {
        val lastCheckIn = checkInRepository
            .findByClientUserIdAndCheckInDateBetweenOrderByCheckInDateDesc(
                clientUserId = clientUserId,
                from = today.minusDays(ENGAGEMENT_LOOKBACK_DAYS),
                to = today,
            )
            .firstOrNull()
            ?.checkInDate
        if (lastCheckIn != null && ChronoUnit.DAYS.between(lastCheckIn, today) < CHECK_IN_IDLE_DAYS) return false
        return send(
            userId = clientUserId,
            kind = ReminderKind.CHECK_IN_IDLE,
            subject = week,
            message = PushMessage(
                channel = PushChannel.SCHEDULE,
                text = PushText.CHECK_IN_IDLE,
                args = listOf(lastCheckIn?.format(CHECK_IN_DATE_FORMAT).orEmpty()),
                data = emptyMap(),
            ),
        )
    }

    private fun daysSinceLabelOf(lastEntry: LocalDate?, today: LocalDate): String {
        if (lastEntry == null) return ChronoUnit.DAYS.between(today.minusDays(DIARY_IDLE_DAYS), today).toString()
        return ChronoUnit.DAYS.between(lastEntry, today).toString()
    }

    private fun send(
        userId: UUID,
        kind: ReminderKind,
        subject: String,
        message: PushMessage,
    ): Boolean {
        val alreadySent = reminderLogRepository.existsByUserIdAndKindAndSubject(
            userId = userId,
            kind = kind.name,
            subject = subject,
        )
        if (alreadySent) return false
        reminderLogRepository.save(
            ReminderLogEntity(
                id = UUID.randomUUID(),
                userId = userId,
                kind = kind.name,
                subject = subject,
                sentAt = Instant.now(clock),
            )
        )
        pushSender.send(userIds = listOf(userId), message = message)
        return true
    }

    private fun weekBucketOf(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return "$year-W$week"
    }
}
