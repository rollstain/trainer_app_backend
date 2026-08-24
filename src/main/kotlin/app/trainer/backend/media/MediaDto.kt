package app.trainer.backend.media

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.util.UUID

private const val FILE_NAME_MAX_LENGTH = 255

data class PrepareUploadRequest(
    @field:NotBlank
    @field:Size(max = FILE_NAME_MAX_LENGTH)
    val fileName: String,
    @field:NotBlank
    val contentType: String,
    @field:Positive
    val sizeBytes: Long,
)

data class PrepareUploadResponse(
    val mediaFileId: UUID,
    val uploadUrl: String,
    val downloadUrl: String,
)

data class MediaFileResponse(
    val id: UUID,
    val contentType: String,
    val sizeBytes: Long,
    val originalName: String,
    val downloadUrl: String,
)
