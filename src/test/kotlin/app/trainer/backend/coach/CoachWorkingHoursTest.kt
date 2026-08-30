package app.trainer.backend.coach

import app.trainer.backend.clientnotes.ClientNoteRepository
import app.trainer.backend.schedule.ScheduleService
import app.trainer.backend.user.UserRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
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

private val COACH_USER_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000011")
private val COACH_ID: UUID = UUID.fromString("00000000-0000-0000-0000-000000000012")
private val CREATED_AT: Instant = Instant.parse("2026-01-01T00:00:00Z")
private val OPENS_AT: LocalTime = LocalTime.of(8, 0)
private val CLOSES_AT: LocalTime = LocalTime.of(20, 0)
private val SATURDAY_OPENS_AT: LocalTime = LocalTime.of(9, 30)
private val SATURDAY_CLOSES_AT: LocalTime = LocalTime.of(14, 0)

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class CoachWorkingHoursTest {

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
    fun `the policy carries the schedule with per-day hours`() {
        givenCoach()
        `when`(workingHourRepository.findByCoachIdOrderByDayOfWeek(COACH_ID)).thenReturn(
            listOf(
                storedDay(dayOfWeek = DayOfWeek.MONDAY.value, opensAt = OPENS_AT, closesAt = CLOSES_AT),
                storedDay(
                    dayOfWeek = DayOfWeek.SATURDAY.value,
                    opensAt = SATURDAY_OPENS_AT,
                    closesAt = SATURDAY_CLOSES_AT,
                ),
            )
        )

        val policy = service.policyOf(coachUserId = COACH_USER_ID)

        assertEquals(
            listOf(
                WorkingDayDto(dayOfWeek = DayOfWeek.MONDAY, opensAt = OPENS_AT, closesAt = CLOSES_AT),
                WorkingDayDto(
                    dayOfWeek = DayOfWeek.SATURDAY,
                    opensAt = SATURDAY_OPENS_AT,
                    closesAt = SATURDAY_CLOSES_AT,
                ),
            ),
            policy.workingHours,
        )
    }

    @Test
    fun `updating the schedule replaces the previous one entirely`() {
        givenCoach()

        service.updatePolicy(
            coachUserId = COACH_USER_ID,
            request = policyRequest(
                workingHours = listOf(
                    WorkingDayDto(dayOfWeek = DayOfWeek.SATURDAY, opensAt = OPENS_AT, closesAt = CLOSES_AT),
                )
            ),
        )

        verify(workingHourRepository).deleteAllOfCoach(COACH_ID)
        val saved = savedDays()
        assertEquals(1, saved.size)
        assertEquals(DayOfWeek.SATURDAY.value, saved.single().dayOfWeek)
        assertEquals(OPENS_AT, saved.single().opensAt)
        assertEquals(CLOSES_AT, saved.single().closesAt)
    }

    @Test
    fun `an empty schedule clears the stored one`() {
        givenCoach()

        service.updatePolicy(coachUserId = COACH_USER_ID, request = policyRequest(workingHours = emptyList()))

        verify(workingHourRepository).deleteAllOfCoach(COACH_ID)
        assertEquals(0, savedDays().size)
    }

    @Test
    fun `a request without the schedule leaves it untouched`() {
        givenCoach()

        service.updatePolicy(coachUserId = COACH_USER_ID, request = policyRequest(workingHours = null))

        verify(workingHourRepository, never()).deleteAllOfCoach(anyNonNull())
        verify(workingHourRepository, never()).saveAll(anyNonNull<List<CoachWorkingHourEntity>>())
    }

    @Test
    fun `a duplicated day is refused`() {
        givenCoach()

        val rejected = assertFailsWith<ResponseStatusException> {
            service.updatePolicy(
                coachUserId = COACH_USER_ID,
                request = policyRequest(
                    workingHours = listOf(
                        WorkingDayDto(dayOfWeek = DayOfWeek.MONDAY, opensAt = OPENS_AT, closesAt = CLOSES_AT),
                        WorkingDayDto(
                            dayOfWeek = DayOfWeek.MONDAY,
                            opensAt = SATURDAY_OPENS_AT,
                            closesAt = SATURDAY_CLOSES_AT,
                        ),
                    )
                ),
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, rejected.statusCode)
        verify(workingHourRepository, never()).deleteAllOfCoach(anyNonNull())
    }

    @Test
    fun `closing before opening is refused`() {
        givenCoach()

        val rejected = assertFailsWith<ResponseStatusException> {
            service.updatePolicy(
                coachUserId = COACH_USER_ID,
                request = policyRequest(
                    workingHours = listOf(
                        WorkingDayDto(dayOfWeek = DayOfWeek.MONDAY, opensAt = CLOSES_AT, closesAt = OPENS_AT),
                    )
                ),
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, rejected.statusCode)
        verify(workingHourRepository, never()).deleteAllOfCoach(anyNonNull())
    }

    private fun policyRequest(workingHours: List<WorkingDayDto>?) = UpdateCoachPolicyRequest(
        cancellationWindowHours = null,
        reminderHour = null,
        sessionRemindersEnabled = null,
        diaryRemindersEnabled = null,
        checkInRemindersEnabled = null,
        workingHours = workingHours,
    )

    private fun storedDay(dayOfWeek: Int, opensAt: LocalTime, closesAt: LocalTime) = CoachWorkingHourEntity(
        id = UUID.randomUUID(),
        coachId = COACH_ID,
        dayOfWeek = dayOfWeek,
        opensAt = opensAt,
        closesAt = closesAt,
    )

    private fun givenCoach() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(
            CoachEntity(
                id = COACH_ID,
                userId = COACH_USER_ID,
                zoneId = "Europe/Moscow",
                cancellationWindowHours = 12,
                reminderHour = 10,
                sessionRemindersEnabled = true,
                diaryRemindersEnabled = true,
                checkInRemindersEnabled = true,
                createdAt = CREATED_AT,
            )
        )
    }

    private fun savedDays(): List<CoachWorkingHourEntity> {
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<CoachWorkingHourEntity>>
        verify(workingHourRepository).saveAll(captor.capture())
        return captor.value
    }
}
