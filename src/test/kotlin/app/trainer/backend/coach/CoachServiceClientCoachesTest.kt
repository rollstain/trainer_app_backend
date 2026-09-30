package app.trainer.backend.coach

import app.trainer.backend.clientnotes.ClientNoteRepository
import app.trainer.backend.program.ProgramService
import app.trainer.backend.push.PushSender
import app.trainer.backend.schedule.ScheduleService
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

private val COACH_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
private val CLIENT_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000003")
private val LINK_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000004")
private val CREATED_AT: Instant = Instant.parse("2026-01-01T00:00:00Z")
private val LINKED_AT: Instant = Instant.parse("2026-09-01T09:30:00Z")
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10

private val TEST_NOW: Instant = Instant.parse("2026-09-23T09:00:00Z")

class CoachServiceClientCoachesTest {

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
    fun `a client's coach carries the moment they were linked`() {
        `when`(coachClientRepository.findByUserId(CLIENT_USER_ID)).thenReturn(
            listOf(
                CoachClientEntity(
                    id = LINK_ID,
                    coachId = COACH_ID,
                    userId = CLIENT_USER_ID,
                    status = CoachClientStatus.ACTIVE,
                    createdAt = LINKED_AT,
                )
            )
        )
        `when`(coachRepository.findById(COACH_ID)).thenReturn(Optional.of(coach()))
        `when`(userRepository.findById(COACH_USER_ID)).thenReturn(Optional.of(coachUser()))

        val coaches = service.coachesOfClient(userId = CLIENT_USER_ID)

        assertEquals(LINKED_AT, coaches.single().linkedAt)
    }

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

    private fun coachUser(): UserEntity = UserEntity(
        id = COACH_USER_ID,
        displayName = "Дмитрий Рогов",
        phone = null,
        email = null,
        login = null,
        isOwner = false,
        createdAt = CREATED_AT,
    )
}
