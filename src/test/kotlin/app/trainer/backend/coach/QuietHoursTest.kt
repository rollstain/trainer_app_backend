package app.trainer.backend.coach

import app.trainer.backend.clientnotes.ClientNoteRepository
import app.trainer.backend.program.ProgramService
import app.trainer.backend.push.PushSender
import app.trainer.backend.schedule.ScheduleService
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val COACH_USER_ID: UUID = UUID.fromString("b0000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("b0000000-0000-0000-0000-000000000002")
private val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")
private val NOW: Instant = Instant.parse("2026-10-09T09:00:00Z")
private val NIGHT_STARTS: LocalTime = LocalTime.of(22, 0)
private val NIGHT_ENDS: LocalTime = LocalTime.of(8, 0)
private val LUNCH_STARTS: LocalTime = LocalTime.of(13, 0)
private val LUNCH_ENDS: LocalTime = LocalTime.of(15, 0)
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 8

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
private fun <T> capturedBy(captor: ArgumentCaptor<*>): T = captor.capture() as T

class QuietHoursTest {

    private val coachRepository = mock(CoachRepository::class.java)
    private val quietHoursRepository = mock(CoachQuietHoursRepository::class.java)

    private val service = CoachService(
        coachRepository = coachRepository,
        coachClientRepository = mock(CoachClientRepository::class.java),
        userRepository = mock(UserRepository::class.java),
        clientNoteRepository = mock(ClientNoteRepository::class.java),
        workingHourRepository = mock(CoachWorkingHourRepository::class.java),
        quietHoursRepository = quietHoursRepository,
        scheduleService = mock(ScheduleService::class.java),
        programService = mock(ProgramService::class.java),
        pushSender = mock(PushSender::class.java),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private val night = QuietWindow(startsAt = NIGHT_STARTS, endsAt = NIGHT_ENDS, zone = MOSCOW)

    @Test
    fun `late in the evening the night started tonight and ends tomorrow morning`() {
        val span = night.spanAt(Instant.parse("2026-10-09T20:30:00Z"))

        assertEquals(QuietSpan(Instant.parse("2026-10-09T19:00:00Z"), Instant.parse("2026-10-10T05:00:00Z")), span)
    }

    @Test
    fun `before dawn the night started yesterday and ends the same morning`() {
        val span = night.spanAt(Instant.parse("2026-10-09T03:00:00Z"))

        assertEquals(QuietSpan(Instant.parse("2026-10-08T19:00:00Z"), Instant.parse("2026-10-09T05:00:00Z")), span)
    }

    @Test
    fun `at the end of quiet hours and in the day nothing is quiet`() {
        assertNull(night.spanAt(Instant.parse("2026-10-09T05:00:00Z")))
        assertNull(night.spanAt(Instant.parse("2026-10-09T12:00:00Z")))
    }

    @Test
    fun `quiet hours inside one day end the same day`() {
        val lunch = QuietWindow(startsAt = LUNCH_STARTS, endsAt = LUNCH_ENDS, zone = MOSCOW)

        assertEquals(
            QuietSpan(Instant.parse("2026-10-09T10:00:00Z"), Instant.parse("2026-10-09T12:00:00Z")),
            lunch.spanAt(Instant.parse("2026-10-09T11:00:00Z")),
        )
        assertNull(lunch.spanAt(Instant.parse("2026-10-09T19:00:00Z")))
    }

    @Test
    fun `the coach turns quiet hours on for the first time`() {
        givenCoach()
        val saved = ArgumentCaptor.forClass(CoachQuietHoursEntity::class.java)

        service.updatePolicy(
            coachUserId = COACH_USER_ID,
            request = policyRequest(QuietHoursDto(true, NIGHT_STARTS, NIGHT_ENDS)),
        )

        verify(quietHoursRepository).save(capturedBy<CoachQuietHoursEntity>(saved))
        assertEquals(true, saved.value.enabled)
        assertEquals(NIGHT_STARTS, saved.value.startsAt)
        assertEquals(NIGHT_ENDS, saved.value.endsAt)
    }

    @Test
    fun `turning quiet hours off keeps the hours for next time`() {
        givenCoach()
        val stored = CoachQuietHoursEntity(
            coachId = COACH_ID,
            enabled = true,
            startsAt = NIGHT_STARTS,
            endsAt = NIGHT_ENDS,
        )
        `when`(quietHoursRepository.findById(COACH_ID)).thenReturn(Optional.of(stored))

        val policy = service.updatePolicy(
            coachUserId = COACH_USER_ID,
            request = policyRequest(QuietHoursDto(false, NIGHT_STARTS, NIGHT_ENDS)),
        )

        assertEquals(QuietHoursDto(enabled = false, startsAt = NIGHT_STARTS, endsAt = NIGHT_ENDS), policy.quietHours)
        verify(quietHoursRepository, never()).save(anyNonNull<CoachQuietHoursEntity>())
    }

    @Test
    fun `quiet hours that start and end at once are refused`() {
        givenCoach()

        val failure = assertFailsWith<ResponseStatusException> {
            service.updatePolicy(
                coachUserId = COACH_USER_ID,
                request = policyRequest(QuietHoursDto(true, NIGHT_ENDS, NIGHT_ENDS)),
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, failure.statusCode)
    }

    @Test
    fun `a coach who never set quiet hours has none`() {
        givenCoach()

        assertNull(service.policyOf(COACH_USER_ID).quietHours)
    }

    private fun policyRequest(quietHours: QuietHoursDto) = UpdateCoachPolicyRequest(
        cancellationWindowHours = null,
        reminderHour = null,
        sessionRemindersEnabled = null,
        diaryRemindersEnabled = null,
        checkInRemindersEnabled = null,
        workingHours = null,
        quietHours = quietHours,
    )

    private fun givenCoach() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(
            CoachEntity(
                id = COACH_ID,
                userId = COACH_USER_ID,
                zoneId = MOSCOW.id,
                cancellationWindowHours = CANCELLATION_WINDOW_HOURS,
                reminderHour = REMINDER_HOUR,
                sessionRemindersEnabled = true,
                diaryRemindersEnabled = true,
                checkInRemindersEnabled = true,
                createdAt = NOW,
            )
        )
    }
}
