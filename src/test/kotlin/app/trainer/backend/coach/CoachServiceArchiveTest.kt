package app.trainer.backend.coach

import app.trainer.backend.clientnotes.ClientNoteRepository
import app.trainer.backend.schedule.ScheduleService
import app.trainer.backend.user.UserRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val COACH_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
private val CLIENT_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
private val CREATED_AT: Instant = Instant.parse("2026-01-01T00:00:00Z")
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10

class CoachServiceArchiveTest {

    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val clientNoteRepository = mock(ClientNoteRepository::class.java)
    private val workingHourRepository = mock(CoachWorkingHourRepository::class.java)
    private val scheduleService = mock(ScheduleService::class.java)

    private val service = CoachService(
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        userRepository = userRepository,
        clientNoteRepository = clientNoteRepository,
        workingHourRepository = workingHourRepository,
        scheduleService = scheduleService,
    )

    @Test
    fun `archiving an active client releases the slots they booked`() {
        val link = link(status = CoachClientStatus.ACTIVE)
        givenCoach()
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(link)

        service.archiveClient(coachUserId = COACH_USER_ID, clientUserId = CLIENT_USER_ID)

        assertEquals(CoachClientStatus.ARCHIVED, link.status)
        verify(scheduleService).releaseBookingsOf(coachId = COACH_ID, clientUserId = CLIENT_USER_ID)
    }

    @Test
    fun `archiving twice is rejected and keeps the schedule untouched`() {
        givenCoach()
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID))
            .thenReturn(link(status = CoachClientStatus.ARCHIVED))

        val failure = assertFailsWith<ResponseStatusException> {
            service.archiveClient(coachUserId = COACH_USER_ID, clientUserId = CLIENT_USER_ID)
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
        verify(scheduleService, never()).releaseBookingsOf(COACH_ID, CLIENT_USER_ID)
    }

    @Test
    fun `archiving somebody else's client is rejected`() {
        givenCoach()
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.archiveClient(coachUserId = COACH_USER_ID, clientUserId = CLIENT_USER_ID)
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    @Test
    fun `a user who is not a coach cannot archive anybody`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.archiveClient(coachUserId = COACH_USER_ID, clientUserId = CLIENT_USER_ID)
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
    }

    private fun givenCoach() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(
            CoachEntity(
                id = COACH_ID,
                userId = COACH_USER_ID,
                zoneId = "Europe/Moscow",
                cancellationWindowHours = CANCELLATION_WINDOW_HOURS,
                reminderHour = REMINDER_HOUR,
                sessionRemindersEnabled = true,
                diaryRemindersEnabled = true,
                checkInRemindersEnabled = true,
                createdAt = CREATED_AT,
            )
        )
    }

    private fun link(status: CoachClientStatus): CoachClientEntity = CoachClientEntity(
        id = UUID.fromString("00000000-0000-0000-0000-000000000004"),
        coachId = COACH_ID,
        userId = CLIENT_USER_ID,
        status = status,
        createdAt = CREATED_AT,
    )
}
