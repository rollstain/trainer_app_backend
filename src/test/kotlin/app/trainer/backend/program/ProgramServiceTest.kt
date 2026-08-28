package app.trainer.backend.program

import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.traininglog.ExerciseEntity
import app.trainer.backend.traininglog.ExerciseKind
import app.trainer.backend.traininglog.ExerciseRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val COACH_USER_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000002")
private val CLIENT_USER_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000003")
private val PROGRAM_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000004")
private val SQUAT_ID: UUID = UUID.fromString("10000000-0000-0000-0000-000000000005")
private val STARTS_ON: LocalDate = LocalDate.of(2026, 3, 2)
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private const val WEEKS_IN_PROGRAM = 2
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10
private const val SETS_COUNT = 4
private const val REPETITIONS = 8

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class ProgramServiceTest {

    private val programRepository = mock(TrainingProgramRepository::class.java)
    private val dayRepository = mock(ProgramDayRepository::class.java)
    private val exerciseLineRepository = mock(ProgramExerciseRepository::class.java)
    private val assignmentRepository = mock(ProgramAssignmentRepository::class.java)
    private val exerciseRepository = mock(ExerciseRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)

    private val service = ProgramService(
        programRepository = programRepository,
        dayRepository = dayRepository,
        exerciseLineRepository = exerciseLineRepository,
        assignmentRepository = assignmentRepository,
        exerciseRepository = exerciseRepository,
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `the planned workout lands on the weekday it was written for`() {
        givenAssignedProgram()

        val planned = service.plannedWorkouts(
            userId = CLIENT_USER_ID,
            from = STARTS_ON,
            to = STARTS_ON.plusDays(1),
        )

        assertEquals(1, planned.size)
        assertEquals(STARTS_ON, planned.first().date)
        assertEquals("День ног", planned.first().dayTitle)
        assertEquals(1, planned.first().weekNumber)
        assertEquals("Приседания", planned.first().exercises.single().exerciseName)
    }

    @Test
    fun `a two-week program starts over on the third week`() {
        givenAssignedProgram()

        val secondWeek = service.plannedWorkouts(
            userId = CLIENT_USER_ID,
            from = STARTS_ON.plusDays(DAYS_IN_A_WEEK),
            to = STARTS_ON.plusDays(DAYS_IN_A_WEEK),
        )
        val thirdWeek = service.plannedWorkouts(
            userId = CLIENT_USER_ID,
            from = STARTS_ON.plusDays(DAYS_IN_A_WEEK * 2),
            to = STARTS_ON.plusDays(DAYS_IN_A_WEEK * 2),
        )

        assertEquals(WEEKS_IN_PROGRAM, secondWeek.single().weekNumber)
        assertEquals(1, thirdWeek.single().weekNumber)
    }

    @Test
    fun `nothing is planned before the program starts`() {
        givenAssignedProgram()

        val planned = service.plannedWorkouts(
            userId = CLIENT_USER_ID,
            from = STARTS_ON.minusDays(3),
            to = STARTS_ON.minusDays(1),
        )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun `a day without exercises is not a planned workout`() {
        givenAssignedProgram(lines = emptyList())

        val planned = service.plannedWorkouts(
            userId = CLIENT_USER_ID,
            from = STARTS_ON,
            to = STARTS_ON.plusDays(DAYS_IN_A_WEEK),
        )

        assertTrue(planned.isEmpty())
    }

    @Test
    fun `an archived program stops being planned`() {
        givenAssignedProgram(archivedAt = NOW)

        val planned = service.plannedWorkouts(
            userId = CLIENT_USER_ID,
            from = STARTS_ON,
            to = STARTS_ON.plusDays(1),
        )

        assertTrue(planned.isEmpty())
        assertNull(service.ownProgram(CLIENT_USER_ID))
    }

    @Test
    fun `assigning a program ends the one the client had`() {
        givenCoach()
        givenActiveClient()
        `when`(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(program()))
        val current = assignment()
        `when`(assignmentRepository.findByClientUserIdAndEndedAtIsNull(CLIENT_USER_ID)).thenReturn(current)

        service.assign(
            coachUserId = COACH_USER_ID,
            programId = PROGRAM_ID,
            request = AssignProgramRequest(clientUserId = CLIENT_USER_ID, startsOn = STARTS_ON),
        )

        assertEquals(NOW, current.endedAt)
    }

    @Test
    fun `archiving a program ends the assignments that used it`() {
        givenCoach()
        val archived = program()
        `when`(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(archived))
        val assignment = assignment()
        `when`(assignmentRepository.findByProgramIdAndEndedAtIsNull(PROGRAM_ID)).thenReturn(listOf(assignment))

        service.archive(coachUserId = COACH_USER_ID, programId = PROGRAM_ID)

        assertEquals(NOW, archived.archivedAt)
        assertEquals(NOW, assignment.endedAt)
    }

    @Test
    fun `duplicating a program copies its days and exercises but not its clients`() {
        givenCoach()
        `when`(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(program()))
        val sourceDays = listOf(day(id = FIRST_WEEK_DAY_ID, weekNumber = 1))
        `when`(dayRepository.findByProgramIdOrderByWeekNumberAscDayOfWeekAsc(PROGRAM_ID))
            .thenReturn(sourceDays)
        `when`(exerciseLineRepository.findByProgramDayIdInOrderByPositionAsc(listOf(FIRST_WEEK_DAY_ID)))
            .thenReturn(listOf(squatLine(FIRST_WEEK_DAY_ID)))
        `when`(exerciseRepository.findAllById(listOf(SQUAT_ID))).thenReturn(listOf(squat()))
        val savedPrograms = recordSaved(programRepository)
        val savedDays = recordSaved(dayRepository)
        val savedLines = recordSaved(exerciseLineRepository)
        `when`(programRepository.findById(anyNonNull())).thenAnswer { invocation ->
            val id = invocation.arguments[0] as UUID
            Optional.ofNullable(
                savedPrograms.firstOrNull { it.id == id } ?: program().takeIf { id == PROGRAM_ID }
            )
        }
        `when`(dayRepository.findByProgramIdOrderByWeekNumberAscDayOfWeekAsc(anyNonNull()))
            .thenAnswer { invocation ->
                val id = invocation.arguments[0] as UUID
                if (id == PROGRAM_ID) sourceDays else savedDays.filter { it.programId == id }
            }
        `when`(exerciseLineRepository.findByProgramDayIdInOrderByPositionAsc(anyNonNull()))
            .thenAnswer { invocation ->
                @Suppress("UNCHECKED_CAST")
                val ids = invocation.arguments[0] as Collection<UUID>
                when {
                    ids.contains(FIRST_WEEK_DAY_ID) -> listOf(squatLine(FIRST_WEEK_DAY_ID))
                    else -> savedLines.filter { it.programDayId in ids }
                }
            }

        val copy = service.duplicate(
            coachUserId = COACH_USER_ID,
            programId = PROGRAM_ID,
            request = DuplicateProgramRequest(title = "Набор массы — копия"),
        )

        assertEquals("Набор массы — копия", copy.title)
        assertEquals(WEEKS_IN_PROGRAM, copy.weeksCount)
        assertEquals(1, savedDays.size)
        assertEquals(1, savedLines.size)
        assertEquals(SQUAT_ID, savedLines.single().exerciseId)
        assertTrue(savedDays.single().programId != PROGRAM_ID)
        verify(assignmentRepository, never()).save(anyNonNull<ProgramAssignmentEntity>())
    }

    @Test
    fun `a program of another coach is not found`() {
        givenCoach()
        val foreign = program(coachId = UUID.fromString("10000000-0000-0000-0000-00000000000f"))
        `when`(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(foreign))

        val failure = assertFailsWith<ResponseStatusException> {
            service.programOf(coachUserId = COACH_USER_ID, programId = PROGRAM_ID)
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    @Test
    fun `a program cannot be assigned to somebody else's client`() {
        givenCoach()
        `when`(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(program()))
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.assign(
                coachUserId = COACH_USER_ID,
                programId = PROGRAM_ID,
                request = AssignProgramRequest(clientUserId = CLIENT_USER_ID, startsOn = STARTS_ON),
            )
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
    }

    @Test
    fun `a week beyond the program length is rejected`() {
        givenCoach()
        `when`(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(program()))

        val failure = assertFailsWith<ResponseStatusException> {
            service.saveDay(
                coachUserId = COACH_USER_ID,
                programId = PROGRAM_ID,
                request = SaveProgramDayRequest(
                    weekNumber = WEEKS_IN_PROGRAM + 1,
                    dayOfWeek = STARTS_ON.dayOfWeek.value,
                    title = "День ног",
                    exercises = emptyList(),
                ),
            )
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
    }

    private inline fun <reified E : Any, R : JpaRepository<E, UUID>> recordSaved(repository: R): MutableList<E> {
        val saved = mutableListOf<E>()
        `when`(repository.save(anyNonNull<E>())).thenAnswer { invocation ->
            val entity = invocation.arguments[0] as E
            saved += entity
            entity
        }
        return saved
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
                createdAt = NOW,
            )
        )
    }

    private fun givenActiveClient() {
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(
            CoachClientEntity(
                id = UUID.fromString("10000000-0000-0000-0000-000000000006"),
                coachId = COACH_ID,
                userId = CLIENT_USER_ID,
                status = CoachClientStatus.ACTIVE,
                createdAt = NOW,
            )
        )
    }

    private fun givenAssignedProgram(
        lines: List<ProgramExerciseEntity> = listOf(squatLine(FIRST_WEEK_DAY_ID)),
        archivedAt: Instant? = null,
    ) {
        `when`(assignmentRepository.findByClientUserIdAndEndedAtIsNull(CLIENT_USER_ID)).thenReturn(assignment())
        `when`(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(program(archivedAt = archivedAt)))
        val days = listOf(
            day(id = FIRST_WEEK_DAY_ID, weekNumber = 1),
            day(id = SECOND_WEEK_DAY_ID, weekNumber = WEEKS_IN_PROGRAM),
        )
        `when`(dayRepository.findByProgramIdOrderByWeekNumberAscDayOfWeekAsc(PROGRAM_ID)).thenReturn(days)
        val allLines = if (lines.isEmpty()) emptyList() else lines + squatLine(SECOND_WEEK_DAY_ID)
        `when`(exerciseLineRepository.findByProgramDayIdInOrderByPositionAsc(days.map { it.id }))
            .thenReturn(allLines)
        `when`(exerciseRepository.findAllById(listOf(SQUAT_ID))).thenReturn(listOf(squat()))
    }

    private fun program(
        coachId: UUID = COACH_ID,
        archivedAt: Instant? = null,
    ): TrainingProgramEntity = TrainingProgramEntity(
        id = PROGRAM_ID,
        coachId = coachId,
        title = "Набор массы",
        weeksCount = WEEKS_IN_PROGRAM,
        createdAt = NOW,
        archivedAt = archivedAt,
    )

    private fun assignment(): ProgramAssignmentEntity = ProgramAssignmentEntity(
        id = UUID.fromString("10000000-0000-0000-0000-000000000007"),
        programId = PROGRAM_ID,
        coachId = COACH_ID,
        clientUserId = CLIENT_USER_ID,
        startsOn = STARTS_ON,
        createdAt = NOW,
        endedAt = null,
    )

    private fun day(id: UUID, weekNumber: Int): ProgramDayEntity = ProgramDayEntity(
        id = id,
        programId = PROGRAM_ID,
        weekNumber = weekNumber,
        dayOfWeek = STARTS_ON.dayOfWeek.value,
        title = "День ног",
    )

    private fun squatLine(dayId: UUID): ProgramExerciseEntity = ProgramExerciseEntity(
        id = UUID.randomUUID(),
        programDayId = dayId,
        exerciseId = SQUAT_ID,
        position = 0,
        setsCount = SETS_COUNT,
        repetitions = REPETITIONS,
        weightGrams = null,
        restSeconds = null,
        note = null,
    )

    private fun squat(): ExerciseEntity = ExerciseEntity(
        id = SQUAT_ID,
        coachId = COACH_ID,
        name = "Приседания",
        muscleGroup = "Ноги",
        kind = ExerciseKind.STRENGTH,
        description = null,
        videoUrl = null,
        videoMediaFileId = null,
        createdAt = NOW,
        archivedAt = null,
    )

    private companion object {
        const val DAYS_IN_A_WEEK = 7L
        val FIRST_WEEK_DAY_ID: UUID = UUID.fromString("10000000-0000-0000-0000-00000000000a")
        val SECOND_WEEK_DAY_ID: UUID = UUID.fromString("10000000-0000-0000-0000-00000000000b")
    }
}
