package app.trainer.backend.coachrequest

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class AskCoachAccessRequest(
    @field:NotBlank
    val telegramUserId: String,
    val telegramDisplayName: String?,
)

data class CoachRequestStatusResponse(
    val status: CoachRequestStatus,
)

data class CoachRequestResponse(
    val id: UUID,
    val telegramUserId: String,
    val telegramDisplayName: String?,
    val createdAt: Instant,
)

data class ApprovedCoachResponse(
    val coachId: UUID,
    val userId: UUID,
    val telegramUserId: String,
)
