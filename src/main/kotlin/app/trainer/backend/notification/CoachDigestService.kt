package app.trainer.backend.notification

import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.reminder.ReminderLogEntity
import app.trainer.backend.reminder.ReminderLogRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val DIGEST_WINDOW_HOURS = 24L
private const val DIGEST_REMINDER_KIND = "COACH_DIGEST"

private val DIGESTED: Map<PushText, PushText> = mapOf(
    PushText.NEW_CHECK_IN to PushText.CHECK_INS_WAITING,
    PushText.NEW_FORM_CHECK to PushText.FORM_CHECKS_WAITING,
)

@Service
class CoachDigestService(
    private val coachRepository: CoachRepository,
    private val notificationRepository: NotificationRepository,
    private val reminderLogRepository: ReminderLogRepository,
    private val pushSender: PushSender,
    private val clock: Clock,
) {

    @Transactional
    fun sendDailyDigests(): Int {
        val now = Instant.now(clock)
        var sent = 0
        coachRepository.findAll().forEach { coach ->
            val zone = digestZoneOf(coach = coach, now = now) ?: return@forEach
            val today = now.atZone(zone).toLocalDate()
            DIGESTED.forEach { (kept, digest) ->
                if (sendDigest(coach = coach, kept = kept, digest = digest, today = today, now = now)) sent++
            }
        }
        return sent
    }

    private fun digestZoneOf(coach: CoachEntity, now: Instant): ZoneId? {
        val zone = runCatching { ZoneId.of(coach.zoneId) }.getOrNull() ?: return null
        return zone.takeIf { now.atZone(it).hour == coach.reminderHour }
    }

    private fun sendDigest(
        coach: CoachEntity,
        kept: PushText,
        digest: PushText,
        today: LocalDate,
        now: Instant,
    ): Boolean {
        val subject = "$today/$kept"
        val alreadySent = reminderLogRepository.existsByUserIdAndKindAndSubject(
            userId = coach.userId,
            kind = DIGEST_REMINDER_KIND,
            subject = subject,
        )
        if (alreadySent) return false
        val waiting = notificationRepository.countByUserIdAndKindAndCreatedAtAfter(
            userId = coach.userId,
            kind = kept,
            createdAt = now.minus(DIGEST_WINDOW_HOURS, ChronoUnit.HOURS),
        )
        if (waiting == 0L) return false
        reminderLogRepository.save(
            ReminderLogEntity(
                id = UUID.randomUUID(),
                userId = coach.userId,
                kind = DIGEST_REMINDER_KIND,
                subject = subject,
                sentAt = now,
            )
        )
        pushSender.send(
            userIds = listOf(coach.userId),
            message = PushMessage(
                channel = PushChannel.CHAT,
                text = digest,
                args = listOf(waiting.toString()),
                data = emptyMap(),
            ),
        )
        return true
    }
}
