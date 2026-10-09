package app.trainer.backend.notification

import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachQuietHoursLookup
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.coach.QuietWindow
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.push.SummaryPart
import app.trainer.backend.push.summaryArgOf
import app.trainer.backend.reminder.ReminderLogRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
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
private const val MORNING_HOUR = 8
private const val LATE_HOUR = 22
private const val NEW_CLIENTS_AT_NIGHT = 2L
private const val BOOKINGS_AT_NIGHT = 1L
private const val CHECK_INS_AT_NIGHT = 2L
private val NIGHT_ENDED: Instant = Instant.parse("2026-10-09T05:00:00Z")
private val TEN_PAST_EIGHT_IN_THE_MORNING: Instant = Instant.parse("2026-10-09T05:10:00Z")
private val TEN_PAST_TEN_IN_THE_EVENING: Instant = Instant.parse("2026-10-08T19:10:00Z")
private val NIGHT: QuietWindow = QuietWindow(
    startsAt = LocalTime.of(LATE_HOUR, 0),
    endsAt = LocalTime.of(MORNING_HOUR, 0),
    zone = ZoneId.of("Europe/Moscow"),
)

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
private fun <T> capturedBy(captor: ArgumentCaptor<*>): T = captor.capture() as T

class CoachDigestServiceTest {

    private val coachRepository = mock(CoachRepository::class.java)
    private val notificationRepository = mock(NotificationRepository::class.java)
    private val reminderLogRepository = mock(ReminderLogRepository::class.java)
    private val settingRepository = mock(NotificationSettingRepository::class.java)
    private val quietHours = mock(CoachQuietHoursLookup::class.java)
    private val pushSender = mock(PushSender::class.java)

    private fun serviceAt(now: Instant) = CoachDigestService(
        coachRepository = coachRepository,
        notificationRepository = notificationRepository,
        settingRepository = settingRepository,
        reminderLogRepository = reminderLogRepository,
        quietHours = quietHours,
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

    @Test
    fun `when quiet hours end the night comes as one morning summary`() {
        givenCoach()
        givenQuietNights()
        givenHeld(PushText.NEW_CLIENT to NEW_CLIENTS_AT_NIGHT, PushText.SLOT_BOOKED to BOOKINGS_AT_NIGHT)
        val message = ArgumentCaptor.forClass(PushMessage::class.java)

        val sent = serviceAt(TEN_PAST_EIGHT_IN_THE_MORNING).sendDailyDigests()

        assertEquals(1, sent)
        verify(pushSender).send(anyNonNull(), capturedBy(message))
        assertEquals(PushText.MORNING_SUMMARY, message.value.text)
        assertEquals(
            listOf(
                summaryArgOf(SummaryPart.NEW_CLIENTS, NEW_CLIENTS_AT_NIGHT),
                summaryArgOf(SummaryPart.SLOT_BOOKINGS, BOOKINGS_AT_NIGHT),
            ),
            message.value.args,
        )
    }

    @Test
    fun `check-ins due at the end of quiet hours ride in the same summary`() {
        givenCoach(reminderHour = MORNING_HOUR)
        givenQuietNights()
        givenWaiting(PushText.NEW_CHECK_IN, CHECK_INS_AT_NIGHT)
        givenHeld(PushText.SLOT_BOOKED to BOOKINGS_AT_NIGHT)
        val message = ArgumentCaptor.forClass(PushMessage::class.java)

        val sent = serviceAt(TEN_PAST_EIGHT_IN_THE_MORNING).sendDailyDigests()

        assertEquals(1, sent)
        verify(pushSender).send(anyNonNull(), capturedBy(message))
        assertEquals(
            listOf(
                summaryArgOf(SummaryPart.CHECK_INS, CHECK_INS_AT_NIGHT),
                summaryArgOf(SummaryPart.SLOT_BOOKINGS, BOOKINGS_AT_NIGHT),
            ),
            message.value.args,
        )
    }

    @Test
    fun `a digest hour inside quiet hours waits`() {
        givenCoach(reminderHour = LATE_HOUR)
        givenQuietNights()
        givenWaiting(PushText.NEW_CHECK_IN, CHECK_INS_WAITING)

        val sent = serviceAt(TEN_PAST_TEN_IN_THE_EVENING).sendDailyDigests()

        assertEquals(0, sent)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a digest hour inside quiet hours comes when they end`() {
        givenCoach(reminderHour = LATE_HOUR)
        givenQuietNights()
        givenWaiting(PushText.NEW_CHECK_IN, CHECK_INS_WAITING)
        val message = ArgumentCaptor.forClass(PushMessage::class.java)

        val sent = serviceAt(TEN_PAST_EIGHT_IN_THE_MORNING).sendDailyDigests()

        assertEquals(1, sent)
        verify(pushSender).send(anyNonNull(), capturedBy(message))
        assertEquals(PushText.CHECK_INS_WAITING, message.value.text)
    }

    @Test
    fun `a night already summarized is not summarized again`() {
        givenCoach()
        givenQuietNights()
        givenHeld(PushText.SLOT_BOOKED to BOOKINGS_AT_NIGHT)
        `when`(reminderLogRepository.existsByUserIdAndKindAndSubject(anyNonNull(), anyNonNull(), anyNonNull()))
            .thenReturn(true)

        val sent = serviceAt(TEN_PAST_EIGHT_IN_THE_MORNING).sendDailyDigests()

        assertEquals(0, sent)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    private fun givenQuietNights() {
        `when`(quietHours.windowOf(anyNonNull(), anyNonNull())).thenReturn(NIGHT)
    }

    private fun givenHeld(vararg counts: Pair<PushText, Long>) {
        `when`(notificationRepository.heldCounts(anyNonNull(), anyNonNull(), anyNonNull())).thenReturn(
            counts.map { (heldKind, heldTotal) ->
                object : HeldNotifications {
                    override val heldUntil: Instant = NIGHT_ENDED
                    override val kind: PushText = heldKind
                    override val total: Long = heldTotal
                }
            }
        )
    }

    private fun givenCoach(reminderHour: Int = DIGEST_HOUR) {
        `when`(coachRepository.findAll()).thenReturn(listOf(coach(reminderHour)))
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

    private fun coach(reminderHour: Int): CoachEntity = CoachEntity(
        id = COACH_ID,
        userId = COACH_USER_ID,
        zoneId = "Europe/Moscow",
        cancellationWindowHours = CANCELLATION_WINDOW_HOURS,
        reminderHour = reminderHour,
        sessionRemindersEnabled = true,
        diaryRemindersEnabled = true,
        checkInRemindersEnabled = true,
        createdAt = AT_NOON,
    )
}
