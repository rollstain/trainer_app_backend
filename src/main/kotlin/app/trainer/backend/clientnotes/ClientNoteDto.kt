package app.trainer.backend.clientnotes

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

private const val NOTE_TITLE_MAX_LENGTH = 200
private const val NOTE_DETAILS_MAX_LENGTH = 4000

data class CreateClientNoteRequest(
    val kind: ClientNoteKind,
    @field:NotBlank
    @field:Size(max = NOTE_TITLE_MAX_LENGTH)
    val title: String,
    @field:Size(max = NOTE_DETAILS_MAX_LENGTH)
    val details: String?,
    val isPinned: Boolean,
)

data class UpdateClientNoteRequest(
    val kind: ClientNoteKind,
    @field:NotBlank
    @field:Size(max = NOTE_TITLE_MAX_LENGTH)
    val title: String,
    @field:Size(max = NOTE_DETAILS_MAX_LENGTH)
    val details: String?,
    val isPinned: Boolean,
)

data class ClientNoteResponse(
    val id: UUID,
    val clientUserId: UUID,
    val kind: ClientNoteKind,
    val title: String,
    val details: String?,
    val isPinned: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
