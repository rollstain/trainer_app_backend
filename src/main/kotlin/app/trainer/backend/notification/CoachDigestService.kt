package app.trainer.backend.notification

import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachQuietHoursLookup
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.coach.QuietWindow
import app.trainer.backend.push.NotificationReason
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.push.SummaryPart
import app.trainer.backend.push.namedSummaryArgOf
import app.trainer.backend.push.summaryArgOf
import app.trainer.backend.reminder.ReminderLogEntity
import app.trainer.backend.reminder.ReminderLogRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

private const val DIGEST_WINDOW_HOURS = 24L
private const val HELD_LOOKBACK_HOURS = 24L
private const val DIGEST_REMINDER_KIND = "COACH_DIGEST"
private const val NIGHT_REMINDER_KIND = "COACH_NIGHT"
private const val ONE_EVENT = 1L

private val DIGESTED: Map<PushText, PushText> = mapOf(
    PushText.NEW_CHECK_IN to PushText.CHECK_INS_WAITING,
    PushText.NEW_FORM_CHECK to PushText.FORM_CHECKS_WAITING,
)

private val SUMMARY_PARTS: Map<PushText, SummaryPart> = mapOf(
    PushText.NEW_CHECK_IN to SummaryPart.CHECK_INS,
    PushText.NEW_FORM_CHECK to SummaryPart.FORM_CHECKS,
    PushText.NEW_CLIENT to SummaryPart.NEW_CLIENTS,
    PushText.CLIENT_UNLINKED to SummaryPart.CLIENTS_LEFT,
    PushText.SLOT_BOOKED to SummaryPart.SLOT_BOOKINGS,
)

@Service
class CoachDigestService(
    private val coachRepository: CoachRepository,
    private val notificationRepository: NotificationRepository,
    private val settingRepository: NotificationSettingRepository,
    private val reminderLogRepository: ReminderLogRepository,
    private val quietHours: CoachQuietHoursLookup,
    private val pushSender: PushSender,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {

    @Transactional
    fun sendDailyDigests(): Int {
        val now = Instant.now(clock)
        var sent = 0
        for (coach in coachRepository.findAll()) {
            val zone = runCatching { ZoneId.of(coach.zoneId) }.getOrNull() ?: continue
            sent += sendFor(coach = coach, zone = zone, now = now)
        }
        return sent
    }

    private fun sendFor(coach: CoachEntity, zone: ZoneId, now: Instant): Int {
        val window = quietHours.windowOf(coach = coach, zone = zone)
        val today = now.atZone(zone).toLocalDate()
        val digestHour = isDigestHour(coach = coach, window = window, zone = zone, now = now)
        val digests = if (digestHour) waitingDigests(coach = coach, today = today, now = now) else emptyMap()
        val nights = releasedNights(coach = coach, now = now)
        if (nights.isEmpty()) {
            for ((kept, waiting) in digests) {
                sendDigest(coach = coach, kept = kept, waiting = waiting, today = today, now = now)
            }
            return digests.size
        }
        sendMorningSummary(coach = coach, digests = digests, nights = nights, today = today, now = now)
        return 1
    }

    private fun isDigestHour(coach: CoachEntity, window: QuietWindow?, zone: ZoneId, now: Instant): Boolean {
        val hour = now.atZone(zone).hour
        if (window == null) return hour == coach.reminderHour
        if (window.spanAt(now) != null) return false
        val digestMovedToMorning = window.covers(LocalTime.of(coach.reminderHour, 0))
        return hour == if (digestMovedToMorning) window.endsAt.hour else coach.reminderHour
    }

    private fun waitingDigests(coach: CoachEntity, today: LocalDate, now: Instant): Map<PushText, Long> {
        val waiting = mutableMapOf<PushText, Long>()
        for (kept in DIGESTED.keys) {
            if (wasSent(coach = coach, kind = DIGEST_REMINDER_KIND, subject = "$today/$kept")) continue
            val count = notificationRepository.countByUserIdAndKindAndCreatedAtAfter(
                userId = coach.userId,
                kind = kept,
                createdAt = now.minus(DIGEST_WINDOW_HOURS, ChronoUnit.HOURS),
            )
            if (count > 0) waiting[kept] = count
        }
        return waiting
    }

    private fun releasedNights(coach: CoachEntity, now: Instant): Map<Instant, Map<PushText, Long>> {
        val held = notificationRepository.heldCounts(
            userId = coach.userId,
            since = now.minus(HELD_LOOKBACK_HOURS, ChronoUnit.HOURS),
            now = now,
        )
        val byNight = held.groupBy(keySelector = { it.heldUntil }, valueTransform = { it.kind to it.total })
        val nights = mutableMapOf<Instant, Map<PushText, Long>>()
        for ((heldUntil, counts) in byNight) {
            if (wasSent(coach = coach, kind = NIGHT_REMINDER_KIND, subject = heldUntil.toString())) continue
            nights[heldUntil] = counts.toMap()
        }
        return nights
    }

    private fun sendDigest(coach: CoachEntity, kept: PushText, waiting: Long, today: LocalDate, now: Instant) {
        remember(coach = coach, kind = DIGEST_REMINDER_KIND, subject = "$today/$kept", now = now)
        pushSender.send(
            userIds = listOf(coach.userId),
            message = PushMessage(
                channel = PushChannel.CHAT,
                text = DIGESTED.getValue(kept),
                args = listOf(waiting.toString()),
                data = emptyMap(),
            ),
        )
    }

    private fun sendMorningSummary(
        coach: CoachEntity,
        digests: Map<PushText, Long>,
        nights: Map<Instant, Map<PushText, Long>>,
        today: LocalDate,
        now: Instant,
    ) {
        val counts = mutableMapOf<SummaryPart, Long>()
        val checkInsMuted = settingRepository
            .findByUserIdAndReason(userId = coach.userId, reason = NotificationReason.NEW_CHECK_INS)
            ?.pushEnabled == false
        if (!checkInsMuted) {
            for ((kept, waiting) in digests) counts.merge(partOf(kept), waiting, Long::plus)
        }
        for (night in nights.values) {
            for ((kind, total) in night) counts.merge(partOf(kind), total, Long::plus)
        }
        for (kept in digests.keys) {
            remember(coach = coach, kind = DIGEST_REMINDER_KIND, subject = "$today/$kept", now = now)
        }
        for (heldUntil in nights.keys) {
            remember(coach = coach, kind = NIGHT_REMINDER_KIND, subject = heldUntil.toString(), now = now)
        }
        pushSender.send(
            userIds = listOf(coach.userId),
            message = PushMessage(
                channel = PushChannel.CHAT,
                text = PushText.MORNING_SUMMARY,
                args = summaryArgsOf(coach = coach, counts = counts, nights = nights),
                data = emptyMap(),
            ),
        )
    }

    private fun summaryArgsOf(
        coach: CoachEntity,
        counts: Map<SummaryPart, Long>,
        nights: Map<Instant, Map<PushText, Long>>,
    ): List<String> {
        val parts = SummaryPart.entries.filter { it in counts }
        val onlyPart = parts.singleOrNull()
        val namesItsEvent = onlyPart != null && onlyPart != SummaryPart.OTHER && counts[onlyPart] == ONE_EVENT
        val name = if (namesItsEvent) nameOfOnlyNightEvent(coach = coach, nights = nights) else null
        if (onlyPart != null && name != null) return listOf(namedSummaryArgOf(onlyPart, name))
        return parts.map { summaryArgOf(it, counts.getValue(it)) }
    }

    private fun nameOfOnlyNightEvent(coach: CoachEntity, nights: Map<Instant, Map<PushText, Long>>): String? {
        val (heldUntil, kinds) = nights.entries.single()
        val event = notificationRepository.findFirstByUserIdAndKindAndHeldUntil(
            userId = coach.userId,
            kind = kinds.keys.single(),
            heldUntil = heldUntil,
        ) ?: return null
        return objectMapper.readValue(event.args, NOTIFICATION_ARGS_TYPE).firstOrNull()
    }

    private fun partOf(kind: PushText): SummaryPart = SUMMARY_PARTS[kind] ?: SummaryPart.OTHER

    private fun wasSent(coach: CoachEntity, kind: String, subject: String): Boolean =
        reminderLogRepository.existsByUserIdAndKindAndSubject(userId = coach.userId, kind = kind, subject = subject)

    private fun remember(coach: CoachEntity, kind: String, subject: String, now: Instant) {
        reminderLogRepository.save(
            ReminderLogEntity(
                id = UUID.randomUUID(),
                userId = coach.userId,
                kind = kind,
                subject = subject,
                sentAt = now,
            )
        )
    }
}
