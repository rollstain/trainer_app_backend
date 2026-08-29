package app.trainer.backend.coachrequest

import java.time.Instant
import java.util.UUID

data class CoachRequestStatusResponse(
    val status: CoachRequestStatus,
)

data class CoachRequestResponse(
    val id: UUID,
    val userId: UUID,
    val displayName: String,
    val createdAt: Instant,
)

data class ApprovedCoachResponse(
    val coachId: UUID,
    val userId: UUID,
)
