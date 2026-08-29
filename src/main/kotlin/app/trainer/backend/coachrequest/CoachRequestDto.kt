package app.trainer.backend.coachrequest

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

const val ABOUT_MIN_LENGTH = 40
const val ABOUT_MAX_LENGTH = 400

data class AskCoachAccessRequest(
    @field:NotBlank
    val displayName: String,
    @field:NotBlank
    @field:Size(min = ABOUT_MIN_LENGTH, max = ABOUT_MAX_LENGTH)
    val about: String,
)

data class CoachAccessStatusResponse(
    val status: CoachRequestStatus?,
    val about: String?,
    val askedAt: Instant?,
    val decidedAt: Instant?,
    val canAskAgainOn: LocalDate?,
)

data class CoachRequestResponse(
    val id: UUID,
    val userId: UUID,
    val displayName: String,
    val about: String,
    val telegramUsername: String?,
    val firstSeenAt: Instant,
    val createdAt: Instant,
    val status: CoachRequestStatus,
    val decidedAt: Instant?,
)

data class ApprovedCoachResponse(
    val coachId: UUID,
    val userId: UUID,
)
