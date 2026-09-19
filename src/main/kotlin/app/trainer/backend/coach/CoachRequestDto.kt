package app.trainer.backend.coach

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CoachRequestStatusResponse(
    val status: CoachRequestStatus,
    val askedAt: Instant,
    val decidedAt: Instant?,
    val canAskAgainOn: LocalDate?,
)

data class TelegramCoachRequestResponse(
    val id: UUID,
    val displayName: String,
    val email: String?,
    val login: String?,
    val telegramUsername: String?,
    val registeredAt: Instant,
    val askedAt: Instant,
)

data class CoachDecisionRequest(
    val approve: Boolean,
    @field:NotBlank
    val telegramUserId: String,
)

data class CoachDecisionResponse(
    val status: CoachRequestStatus,
    val displayName: String,
)
