package app.trainer.backend.habit

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal const val HABIT_TITLE_MAX_LENGTH = 120
private const val HABITS_IN_SET_MAX = 20

data class CreateHabitRequest(
    @field:NotBlank
    @field:Size(max = HABIT_TITLE_MAX_LENGTH)
    val title: String,
)

data class HabitResponse(
    val id: UUID,
    val clientUserId: UUID,
    val title: String,
    val isSetByCoach: Boolean,
    val doneDates: List<LocalDate>,
)

data class HabitSetRequest(
    @field:NotBlank
    @field:Size(max = HABIT_TITLE_MAX_LENGTH)
    val title: String,
    @field:Size(min = 1, max = HABITS_IN_SET_MAX)
    val habits: List<String>,
)

data class AssignHabitSetRequest(
    @field:Size(min = 1, max = HABITS_IN_SET_MAX)
    val habits: List<String>,
)

data class HabitSetResponse(
    val id: UUID,
    val title: String,
    val habits: List<String>,
    val assignedClientsCount: Int,
    val assignedClientNames: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant?,
)
