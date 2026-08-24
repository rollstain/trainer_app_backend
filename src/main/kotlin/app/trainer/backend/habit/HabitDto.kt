package app.trainer.backend.habit

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

private const val TITLE_MAX_LENGTH = 120

data class CreateHabitRequest(
    @field:NotBlank
    @field:Size(max = TITLE_MAX_LENGTH)
    val title: String,
)

data class HabitResponse(
    val id: UUID,
    val clientUserId: UUID,
    val title: String,
    val isSetByCoach: Boolean,
    val doneDates: List<LocalDate>,
)
