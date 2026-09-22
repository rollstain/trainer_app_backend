package app.trainer.backend.coach

import app.trainer.backend.clientnotes.ClientNoteRepository
import app.trainer.backend.program.ProgramService
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.schedule.ScheduleService
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
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

private val TEST_NOW: Instant = Instant.parse("2026-09-23T09:00:00Z")

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
private fun <T> capturedBy(captor: ArgumentCaptor<*>): T = captor.capture() as T

class CoachServiceArchiveTest {

    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val clientNoteRepository = mock(ClientNoteRepository::class.java)
    private val workingHourRepository = mock(CoachWorkingHourRepository::class.java)
    private val scheduleService = mock(ScheduleService::class.java)
    private val programService = mock(ProgramService::class.java)
    private val pushSender = mock(PushSender::class.java)

    private val service = CoachService(
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        userRepository = userRepository,
        clientNoteRepository = clientNoteRepository,
        workingHourRepository = workingHourRepository,
        scheduleService = scheduleService,
        programService = programService,
        pushSender = pushSender,
        clock = Clock.fixed(TEST_NOW, ZoneOffset.UTC),
    )

    @Test
    fun `archiving an active client releases the slots they booked`() {
        val link = link(status = CoachClientStatus.ACTIVE)
        givenCoach()
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(link)

        service.archiveClient(coachUserId = COACH_USER_ID, clientUserId = CLIENT_USER_ID)

        assertEquals(CoachClientStatus.ARCHIVED, link.status)
        assertEquals(TEST_NOW, link.endedAt)
        assertEquals(LinkEndedBy.COACH, link.endedBy)
        verify(scheduleService).releaseBookingsOf(coachId = COACH_ID, clientUserId = CLIENT_USER_ID)
        verify(programService).endAssignmentQuietly(CLIENT_USER_ID)
    }

    @Test
    fun `a client who leaves ends the link themselves and the coach hears about it`() {
        val link = link(status = CoachClientStatus.ACTIVE)
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(link)
        `when`(coachRepository.findById(COACH_ID)).thenReturn(Optional.of(coach()))
        `when`(userRepository.findById(CLIENT_USER_ID)).thenReturn(Optional.of(client()))
        val message = ArgumentCaptor.forClass(PushMessage::class.java)

        service.leaveCoach(userId = CLIENT_USER_ID, coachId = COACH_ID)

        assertEquals(CoachClientStatus.ARCHIVED, link.status)
        assertEquals(LinkEndedBy.CLIENT, link.endedBy)
        verify(scheduleService).releaseBookingsOf(coachId = COACH_ID, clientUserId = CLIENT_USER_ID)
        verify(programService).endAssignmentQuietly(CLIENT_USER_ID)
        verify(pushSender).send(anyNonNull(), capturedBy(message))
        assertEquals(PushText.CLIENT_UNLINKED, message.value.text)
        assertEquals(listOf("Анна"), message.value.args)
    }

    @Test
    fun `leaving a coach who does not lead you is rejected`() {
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.leaveCoach(userId = CLIENT_USER_ID, coachId = COACH_ID)
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `past clients come back newest first with who ended it`() {
        givenCoach()
        val left = link(status = CoachClientStatus.ARCHIVED).apply {
            endedAt = TEST_NOW
            endedBy = LinkEndedBy.CLIENT
        }
        `when`(coachClientRepository.findByCoachIdAndStatus(COACH_ID, CoachClientStatus.ARCHIVED))
            .thenReturn(listOf(left))
        `when`(userRepository.findAllById(anyNonNull<Iterable<UUID>>())).thenReturn(listOf(client()))

        val past = service.pastClients(COACH_USER_ID)

        assertEquals(1, past.size)
        assertEquals(TEST_NOW, past.single().endedAt)
        assertEquals(LinkEndedBy.CLIENT, past.single().endedBy)
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

    private fun client(): UserEntity = UserEntity(
        id = CLIENT_USER_ID,
        displayName = "Анна",
        phone = null,
        email = null,
        login = null,
        isOwner = false,
        createdAt = CREATED_AT,
    )

    private fun coach(): CoachEntity = CoachEntity(
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
