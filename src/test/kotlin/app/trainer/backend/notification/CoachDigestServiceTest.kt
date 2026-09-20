package app.trainer.backend.notification

import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.reminder.ReminderLogRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private val COACH_USER_ID: UUID = UUID.fromString("95000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("95000000-0000-0000-0000-000000000002")
private val AT_EIGHT_IN_THE_EVENING: Instant = Instant.parse("2026-09-20T17:10:00Z")
private val AT_NOON: Instant = Instant.parse("2026-09-20T09:10:00Z")
private const val CANCELLATION_WINDOW_HOURS = 12
private const val DIGEST_HOUR = 20
private const val CHECK_INS_WAITING = 3L

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
private fun <T> capturedBy(captor: ArgumentCaptor<*>): T = captor.capture() as T

class CoachDigestServiceTest {

    private val coachRepository = mock(CoachRepository::class.java)
    private val notificationRepository = mock(NotificationRepository::class.java)
    private val reminderLogRepository = mock(ReminderLogRepository::class.java)
    private val pushSender = mock(PushSender::class.java)

    private fun serviceAt(now: Instant) = CoachDigestService(
        coachRepository = coachRepository,
        notificationRepository = notificationRepository,
        reminderLogRepository = reminderLogRepository,
        pushSender = pushSender,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    @Test
    fun `at the coach hour the day of check-ins comes as one push`() {
        givenCoach()
        givenWaiting(PushText.NEW_CHECK_IN, CHECK_INS_WAITING)
        val message = ArgumentCaptor.forClass(PushMessage::class.java)

        val sent = serviceAt(AT_EIGHT_IN_THE_EVENING).sendDailyDigests()

        assertEquals(1, sent)
        verify(pushSender).send(anyNonNull(), capturedBy(message))
        assertEquals(PushText.CHECK_INS_WAITING, message.value.text)
        assertEquals(listOf(CHECK_INS_WAITING.toString()), message.value.args)
    }

    @Test
    fun `at any other hour the digest waits`() {
        givenCoach()
        givenWaiting(PushText.NEW_CHECK_IN, CHECK_INS_WAITING)

        val sent = serviceAt(AT_NOON).sendDailyDigests()

        assertEquals(0, sent)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `nothing waiting means no push`() {
        givenCoach()
        givenWaiting(PushText.NEW_CHECK_IN, 0L)

        val sent = serviceAt(AT_EIGHT_IN_THE_EVENING).sendDailyDigests()

        assertEquals(0, sent)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `the same day gets one digest, not one an hour`() {
        givenCoach()
        givenWaiting(PushText.NEW_CHECK_IN, CHECK_INS_WAITING)
        `when`(reminderLogRepository.existsByUserIdAndKindAndSubject(anyNonNull(), anyNonNull(), anyNonNull()))
            .thenReturn(true)

        val sent = serviceAt(AT_EIGHT_IN_THE_EVENING).sendDailyDigests()

        assertEquals(0, sent)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    private fun givenCoach() {
        `when`(coachRepository.findAll()).thenReturn(listOf(coach()))
    }

    private fun givenWaiting(kind: PushText, count: Long) {
        `when`(
            notificationRepository.countByUserIdAndKindAndCreatedAtAfter(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
            )
        ).thenAnswer { invocation -> if (invocation.arguments[1] == kind) count else 0L }
    }

    private fun coach(): CoachEntity = CoachEntity(
        id = COACH_ID,
        userId = COACH_USER_ID,
        zoneId = "Europe/Moscow",
        cancellationWindowHours = CANCELLATION_WINDOW_HOURS,
        reminderHour = DIGEST_HOUR,
        sessionRemindersEnabled = true,
        diaryRemindersEnabled = true,
        checkInRemindersEnabled = true,
        createdAt = AT_NOON,
    )
}
