package app.trainer.backend.program

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

private const val MIN_WEEKS = 1L
private const val MAX_WEEKS = 12L
private const val MIN_DAY_OF_WEEK = 1L
private const val MAX_DAY_OF_WEEK = 7L
private const val MIN_SETS = 1L
private const val MAX_SETS = 20L
private const val MAX_TITLE_LENGTH = 120
private const val MAX_NOTE_LENGTH = 280
private const val MAX_EXERCISES_PER_DAY = 30

data class ProgramSummaryResponse(
    val id: UUID,
    val title: String,
    val weeksCount: Int,
    val filledDaysCount: Int,
    val assignedClientsCount: Int,
)

data class ProgramExerciseResponse(
    val exerciseId: UUID,
    val exerciseName: String,
    val position: Int,
    val setsCount: Int,
    val repetitions: Int?,
    val weightGrams: Int?,
    val restSeconds: Int?,
    val note: String?,
)

data class ProgramDayResponse(
    val weekNumber: Int,
    val dayOfWeek: Int,
    val title: String,
    val exercises: List<ProgramExerciseResponse>,
)

data class ProgramResponse(
    val id: UUID,
    val title: String,
    val weeksCount: Int,
    val days: List<ProgramDayResponse>,
)

data class CreateProgramRequest(
    @field:NotBlank
    @field:Size(max = MAX_TITLE_LENGTH)
    val title: String,
    @field:Min(MIN_WEEKS)
    @field:Max(MAX_WEEKS)
    val weeksCount: Int,
)

data class DuplicateProgramRequest(
    @field:NotBlank
    @field:Size(max = MAX_TITLE_LENGTH)
    val title: String,
)

data class ProgramExerciseRequest(
    val exerciseId: UUID,
    @field:Min(MIN_SETS)
    @field:Max(MAX_SETS)
    val setsCount: Int,
    val repetitions: Int?,
    val weightGrams: Int?,
    val restSeconds: Int?,
    @field:Size(max = MAX_NOTE_LENGTH)
    val note: String?,
)

data class SaveProgramDayRequest(
    @field:Min(MIN_WEEKS)
    @field:Max(MAX_WEEKS)
    val weekNumber: Int,
    @field:Min(MIN_DAY_OF_WEEK)
    @field:Max(MAX_DAY_OF_WEEK)
    val dayOfWeek: Int,
    @field:Size(max = MAX_TITLE_LENGTH)
    val title: String,
    @field:Size(max = MAX_EXERCISES_PER_DAY)
    val exercises: List<ProgramExerciseRequest>,
)

data class AssignProgramRequest(
    val clientUserId: UUID,
    val startsOn: LocalDate,
)

data class PlannedWorkoutResponse(
    val date: LocalDate,
    val programTitle: String,
    val dayTitle: String,
    val weekNumber: Int,
    val exercises: List<ProgramExerciseResponse>,
)

data class ClientProgramResponse(
    val programId: UUID,
    val programTitle: String,
    val startsOn: LocalDate,
)

data class ClientProgramStateResponse(val program: ClientProgramResponse?)
