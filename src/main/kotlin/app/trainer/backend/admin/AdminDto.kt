package app.trainer.backend.admin

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class CreateCoachRequest(
    @field:NotBlank
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
