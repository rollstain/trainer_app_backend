package app.trainer.backend.formcheck

import app.trainer.backend.media.MediaFileResponse
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

private const val NOTE_MAX_LENGTH = 2000

data class CreateFormCheckRequest(
    val mediaFileId: UUID,
    val exerciseId: UUID?,
    @field:Size(max = NOTE_MAX_LENGTH)
    val note: String?,
)

data class ReviewFormCheckRequest(
    @field:Size(max = NOTE_MAX_LENGTH)
    val comment: String?,
)

data class FormCheckResponse(
    val id: UUID,
    val clientUserId: UUID,
    val clientDisplayName: String,
    val exerciseId: UUID?,
    val exerciseName: String?,
    val video: MediaFileResponse?,
    val note: String?,
    val coachComment: String?,
    val isReviewed: Boolean,
    val createdAt: Instant,
)
