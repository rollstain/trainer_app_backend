package app.trainer.backend.clientnotes

import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val COACH_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000011")
private val COACH_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000012")
private val CLIENT_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000013")
private val NOTE_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000014")
private val CREATED_AT: Instant = Instant.parse("2026-01-01T00:00:00Z")
private val TEST_NOW: Instant = Instant.parse("2026-09-30T09:00:00Z")
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10

class ClientNoteAccessTest {

    private val noteRepository = mock(ClientNoteRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)

    private val service = ClientNoteService(
        noteRepository = noteRepository,
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        clock = Clock.fixed(TEST_NOW, ZoneOffset.UTC),
    )

    @BeforeEach
    fun givenCoach() {
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

    @Test
    fun `the coach still reads notes about a client who has left`() {
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID))
            .thenReturn(link(CoachClientStatus.ARCHIVED))
        `when`(noteRepository.findByCoachIdAndClientUserIdAndArchivedAtIsNull(COACH_ID, CLIENT_USER_ID))
            .thenReturn(listOf(note()))

        val notes = service.notesOfClient(coachUserId = COACH_USER_ID, clientUserId = CLIENT_USER_ID)

        assertEquals(listOf(NOTE_ID), notes.map { it.id })
    }

    @Test
    fun `a client who has left gets no new notes`() {
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID))
            .thenReturn(link(CoachClientStatus.ARCHIVED))

        val failure = assertFailsWith<ResponseStatusException> {
            service.create(
                coachUserId = COACH_USER_ID,
                clientUserId = CLIENT_USER_ID,
                request = CreateClientNoteRequest(
                    kind = ClientNoteKind.GENERAL,
                    title = "Позвонить",
                    details = null,
                    isPinned = false,
                ),
            )
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
    }

    @Test
    fun `notes about somebody the coach never led stay closed`() {
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.notesOfClient(coachUserId = COACH_USER_ID, clientUserId = CLIENT_USER_ID)
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
    }

    private fun link(status: CoachClientStatus): CoachClientEntity = CoachClientEntity(
        id = UUID.fromString("00000000-0000-0000-0000-000000000015"),
        coachId = COACH_ID,
        userId = CLIENT_USER_ID,
        status = status,
        createdAt = CREATED_AT,
    )

    private fun note(): ClientNoteEntity = ClientNoteEntity(
        id = NOTE_ID,
        coachId = COACH_ID,
        clientUserId = CLIENT_USER_ID,
        kind = ClientNoteKind.GENERAL,
        title = "Колено после операции",
        details = null,
        isPinned = false,
        createdAt = CREATED_AT,
        updatedAt = CREATED_AT,
        archivedAt = null,
    )
}
