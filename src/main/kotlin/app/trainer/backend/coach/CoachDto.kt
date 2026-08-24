package app.trainer.backend.coach

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.util.UUID

private const val MIN_CANCELLATION_WINDOW_HOURS = 0L
private const val MAX_CANCELLATION_WINDOW_HOURS = 168L

data class CoachClientResponse(
    val userId: UUID,
    val displayName: String,
    val status: CoachClientStatus,
)

data class CoachSummaryResponse(
    val coachId: UUID,
    val userId: UUID,
    val displayName: String,
    val zoneId: String,
    val cancellationWindowHours: Int,
)

data class UpdateCoachPolicyRequest(
    @field:Min(MIN_CANCELLATION_WINDOW_HOURS)
    @field:Max(MAX_CANCELLATION_WINDOW_HOURS)
    val cancellationWindowHours: Int,
)

data class CoachPolicyResponse(val cancellationWindowHours: Int)
