package app.trainer.backend.admin

import app.trainer.backend.user.DISPLAY_NAME_MAX_LENGTH
import app.trainer.backend.user.DISPLAY_NAME_TOO_LONG
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateCoachRequest(
    @field:NotBlank
    @field:Size(max = DISPLAY_NAME_MAX_LENGTH, message = DISPLAY_NAME_TOO_LONG)
    val displayName: String,
    @field:NotBlank
    val zoneId: String,
    val phone: String?,
    val email: String?,
)

data class CoachOnboardedResponse(
    val coachId: UUID,
    val userId: UUID,
    val code: String,
    val expiresAt: Instant,
)

data class LoginCodeResponse(
    val coachId: UUID,
    val code: String,
    val expiresAt: Instant,
)
