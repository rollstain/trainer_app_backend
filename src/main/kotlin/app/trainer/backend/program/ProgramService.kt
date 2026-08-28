package app.trainer.backend.program

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.traininglog.ExerciseEntity
import app.trainer.backend.traininglog.ExerciseOwnerKind
import app.trainer.backend.traininglog.ExerciseRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val DAYS_IN_WEEK = 7
private const val MAX_PLANNED_RANGE_DAYS = 62L

@Service
class ProgramService(
    private val programRepository: TrainingProgramRepository,
    private val dayRepository: ProgramDayRepository,
    private val exerciseLineRepository: ProgramExerciseRepository,
    private val assignmentRepository: ProgramAssignmentRepository,
    private val exerciseRepository: ExerciseRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun programsOf(coachUserId: UUID): List<ProgramSummaryResponse> {
        val coach = requireCoach(coachUserId)
        val programs = programRepository.findByCoachIdAndArchivedAtIsNullOrderByCreatedAtDesc(coach.id)
        val assignedByProgram = assignmentRepository
            .findByCoachIdAndEndedAtIsNull(coach.id)
            .groupingBy { it.programId }
            .eachCount()
        return programs.map { program ->
            val days = dayRepository.findByProgramIdOrderByWeekNumberAscDayOfWeekAsc(program.id)
            val filled = exerciseLineRepository
                .findByProgramDayIdInOrderByPositionAsc(days.map { it.id })
                .map { it.programDayId }
                .distinct()
                .size
            ProgramSummaryResponse(
                id = program.id,
                title = program.title,
                weeksCount = program.weeksCount,
                filledDaysCount = filled,
                assignedClientsCount = assignedByProgram[program.id] ?: 0,
            )
        }
    }

    @Transactional
    fun create(coachUserId: UUID, request: CreateProgramRequest): ProgramResponse {
        val coach = requireCoach(coachUserId)
        val program = TrainingProgramEntity(
            id = UUID.randomUUID(),
            coachId = coach.id,
            title = request.title.trim(),
            weeksCount = request.weeksCount,
            createdAt = Instant.now(clock),
            archivedAt = null,
        )
        programRepository.save(program)
        return toResponse(program = program, days = emptyList(), lines = emptyList())
    }

    @Transactional
    fun duplicate(coachUserId: UUID, programId: UUID, request: DuplicateProgramRequest): ProgramResponse {
        val coach = requireCoach(coachUserId)
        val source = requireOwnProgram(coach = coach, programId = programId)
        val copy = TrainingProgramEntity(
            id = UUID.randomUUID(),
            coachId = coach.id,
            title = request.title.trim(),
            weeksCount = source.weeksCount,
            createdAt = Instant.now(clock),
            archivedAt = null,
        )
        programRepository.save(copy)

        val sourceDays = dayRepository.findByProgramIdOrderByWeekNumberAscDayOfWeekAsc(source.id)
        val sourceLines = exerciseLineRepository
            .findByProgramDayIdInOrderByPositionAsc(sourceDays.map { it.id })
            .groupBy { it.programDayId }
        sourceDays.forEach { day ->
            val copiedDay = ProgramDayEntity(
                id = UUID.randomUUID(),
                programId = copy.id,
                weekNumber = day.weekNumber,
                dayOfWeek = day.dayOfWeek,
                title = day.title,
            )
            dayRepository.save(copiedDay)
            sourceLines[day.id].orEmpty().forEach { line ->
                exerciseLineRepository.save(
                    ProgramExerciseEntity(
                        id = UUID.randomUUID(),
                        programDayId = copiedDay.id,
                        exerciseId = line.exerciseId,
                        position = line.position,
                        setsCount = line.setsCount,
                        repetitions = line.repetitions,
                        weightGrams = line.weightGrams,
                        restSeconds = line.restSeconds,
                        note = line.note,
                    )
                )
            }
        }
        return programOf(coachUserId = coachUserId, programId = copy.id)
    }

    @Transactional(readOnly = true)
    fun programOf(coachUserId: UUID, programId: UUID): ProgramResponse {
        val coach = requireCoach(coachUserId)
        val program = requireOwnProgram(coach = coach, programId = programId)
        val days = dayRepository.findByProgramIdOrderByWeekNumberAscDayOfWeekAsc(program.id)
        val lines = exerciseLineRepository.findByProgramDayIdInOrderByPositionAsc(days.map { it.id })
        return toResponse(program = program, days = days, lines = lines)
    }

    @Transactional
    fun saveDay(coachUserId: UUID, programId: UUID, request: SaveProgramDayRequest): ProgramResponse {
        val coach = requireCoach(coachUserId)
        val program = requireOwnProgram(coach = coach, programId = programId)
        if (request.weekNumber > program.weeksCount) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "В программе меньше недель")
        }
        val day = dayRepository.findByProgramIdAndWeekNumberAndDayOfWeek(
            programId = program.id,
            weekNumber = request.weekNumber,
            dayOfWeek = request.dayOfWeek,
        ) ?: ProgramDayEntity(
            id = UUID.randomUUID(),
            programId = program.id,
            weekNumber = request.weekNumber,
            dayOfWeek = request.dayOfWeek,
            title = request.title.trim(),
        )
        day.title = request.title.trim()
        dayRepository.save(day)
        exerciseLineRepository.deleteByProgramDayId(day.id)
        requireKnownExercises(coach = coach, exerciseIds = request.exercises.map { it.exerciseId })
        request.exercises.forEachIndexed { index, line ->
            exerciseLineRepository.save(
                ProgramExerciseEntity(
                    id = UUID.randomUUID(),
                    programDayId = day.id,
                    exerciseId = line.exerciseId,
                    position = index,
                    setsCount = line.setsCount,
                    repetitions = line.repetitions,
                    weightGrams = line.weightGrams,
                    restSeconds = line.restSeconds,
                    note = line.note?.trim()?.takeIf { it.isNotEmpty() },
                )
            )
        }
        return programOf(coachUserId = coachUserId, programId = programId)
    }

    @Transactional
    fun archive(coachUserId: UUID, programId: UUID) {
        val coach = requireCoach(coachUserId)
        val program = requireOwnProgram(coach = coach, programId = programId)
        program.archivedAt = Instant.now(clock)
        assignmentRepository.findByProgramIdAndEndedAtIsNull(program.id).forEach { assignment ->
            assignment.endedAt = Instant.now(clock)
        }
    }

    @Transactional
    fun assign(coachUserId: UUID, programId: UUID, request: AssignProgramRequest): ClientProgramResponse {
        val coach = requireCoach(coachUserId)
        val program = requireOwnProgram(coach = coach, programId = programId)
        requireOwnClient(coach = coach, clientUserId = request.clientUserId)
        assignmentRepository.findByClientUserIdAndEndedAtIsNull(request.clientUserId)?.let { current ->
            current.endedAt = Instant.now(clock)
        }
        val assignment = ProgramAssignmentEntity(
            id = UUID.randomUUID(),
            programId = program.id,
            coachId = coach.id,
            clientUserId = request.clientUserId,
            startsOn = request.startsOn,
            createdAt = Instant.now(clock),
            endedAt = null,
        )
        assignmentRepository.save(assignment)
        return ClientProgramResponse(
            programId = program.id,
            programTitle = program.title,
            startsOn = assignment.startsOn,
        )
    }

    @Transactional
    fun endAssignment(coachUserId: UUID, clientUserId: UUID) {
        val coach = requireCoach(coachUserId)
        requireOwnClient(coach = coach, clientUserId = clientUserId)
        val assignment = assignmentRepository.findByClientUserIdAndEndedAtIsNull(clientUserId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "У подопечного нет программы")
        if (assignment.coachId != coach.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Программу назначил другой тренер")
        }
        assignment.endedAt = Instant.now(clock)
    }

    @Transactional(readOnly = true)
    fun clientProgram(coachUserId: UUID, clientUserId: UUID): ClientProgramResponse? {
        val coach = requireCoach(coachUserId)
        requireOwnClient(coach = coach, clientUserId = clientUserId)
        return activeProgramOf(clientUserId)
    }

    @Transactional(readOnly = true)
    fun ownProgram(userId: UUID): ClientProgramResponse? = activeProgramOf(userId)

    @Transactional(readOnly = true)
    fun plannedWorkouts(userId: UUID, from: LocalDate, to: LocalDate): List<PlannedWorkoutResponse> {
        if (from.isAfter(to)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Начало периода позже конца")
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_PLANNED_RANGE_DAYS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Слишком длинный период")
        }
        val assignment = assignmentRepository.findByClientUserIdAndEndedAtIsNull(userId) ?: return emptyList()
        val program = programRepository.findByIdOrNull(assignment.programId) ?: return emptyList()
        if (program.archivedAt != null) return emptyList()

        val days = dayRepository.findByProgramIdOrderByWeekNumberAscDayOfWeekAsc(program.id)
        if (days.isEmpty()) return emptyList()
        val linesByDay = exerciseLineRepository
            .findByProgramDayIdInOrderByPositionAsc(days.map { it.id })
            .groupBy { it.programDayId }
        val names = exerciseNamesOf(linesByDay.values.flatten())

        return generateSequence(from) { current -> current.plusDays(1).takeIf { !it.isAfter(to) } }
            .mapNotNull { date ->
                val day = dayFor(program = program, assignment = assignment, days = days, date = date)
                    ?: return@mapNotNull null
                val lines = linesByDay[day.id].orEmpty()
                if (lines.isEmpty()) return@mapNotNull null
                PlannedWorkoutResponse(
                    date = date,
                    programTitle = program.title,
                    dayTitle = day.title,
                    weekNumber = day.weekNumber,
                    exercises = lines.map { toResponse(line = it, names = names) },
                )
            }
            .toList()
    }

    private fun dayFor(
        program: TrainingProgramEntity,
        assignment: ProgramAssignmentEntity,
        days: List<ProgramDayEntity>,
        date: LocalDate,
    ): ProgramDayEntity? {
        val daysSinceStart = ChronoUnit.DAYS.between(assignment.startsOn, date)
        if (daysSinceStart < 0) return null
        val weekNumber = ((daysSinceStart / DAYS_IN_WEEK) % program.weeksCount).toInt() + 1
        return days.firstOrNull { it.weekNumber == weekNumber && it.dayOfWeek == date.dayOfWeek.value }
    }

    private fun activeProgramOf(clientUserId: UUID): ClientProgramResponse? {
        val assignment = assignmentRepository.findByClientUserIdAndEndedAtIsNull(clientUserId) ?: return null
        val program = programRepository.findByIdOrNull(assignment.programId) ?: return null
        if (program.archivedAt != null) return null
        return ClientProgramResponse(
            programId = program.id,
            programTitle = program.title,
            startsOn = assignment.startsOn,
        )
    }

    private fun toResponse(
        program: TrainingProgramEntity,
        days: List<ProgramDayEntity>,
        lines: List<ProgramExerciseEntity>,
    ): ProgramResponse {
        val names = exerciseNamesOf(lines)
        val linesByDay = lines.groupBy { it.programDayId }
        return ProgramResponse(
            id = program.id,
            title = program.title,
            weeksCount = program.weeksCount,
            days = days.map { day ->
                ProgramDayResponse(
                    weekNumber = day.weekNumber,
                    dayOfWeek = day.dayOfWeek,
                    title = day.title,
                    exercises = linesByDay[day.id].orEmpty().map { toResponse(line = it, names = names) },
                )
            },
        )
    }

    private fun toResponse(line: ProgramExerciseEntity, names: Map<UUID, String>): ProgramExerciseResponse =
        ProgramExerciseResponse(
            exerciseId = line.exerciseId,
            exerciseName = names[line.exerciseId].orEmpty(),
            position = line.position,
            setsCount = line.setsCount,
            repetitions = line.repetitions,
            weightGrams = line.weightGrams,
            restSeconds = line.restSeconds,
            note = line.note,
        )

    private fun exerciseNamesOf(lines: List<ProgramExerciseEntity>): Map<UUID, String> {
        if (lines.isEmpty()) return emptyMap()
        return exerciseRepository
            .findAllById(lines.map { it.exerciseId }.distinct())
            .associate { it.id to it.name }
    }

    private fun requireKnownExercises(coach: CoachEntity, exerciseIds: List<UUID>) {
        if (exerciseIds.isEmpty()) return
        val available = exerciseRepository
            .findAllById(exerciseIds.distinct())
            .filter(::isUsable)
            .filter { it.ownerKind == ExerciseOwnerKind.SHARED || it.ownerId == coach.id }
            .map { it.id }
            .toSet()
        if (!available.containsAll(exerciseIds.toSet())) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Упражнение недоступно")
        }
    }

    private fun isUsable(exercise: ExerciseEntity): Boolean = exercise.archivedAt == null

    private fun requireOwnProgram(coach: CoachEntity, programId: UUID): TrainingProgramEntity {
        val program = programRepository.findByIdOrNull(programId)
        if (program == null || program.coachId != coach.id || program.archivedAt != null) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Программа не найдена")
        }
        return program
    }

    private fun requireOwnClient(coach: CoachEntity, clientUserId: UUID) {
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coach.id, userId = clientUserId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваш подопечный")
        }
    }

    private fun requireCoach(coachUserId: UUID): CoachEntity = coachRepository.findByUserId(coachUserId)
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")
}
