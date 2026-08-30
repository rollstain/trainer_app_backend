package app.trainer.backend.chat

import app.trainer.backend.media.MediaFileResponse
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

private const val MESSAGE_BODY_MAX_LENGTH = 4000

data class SendMessageRequest(
    val clientMessageId: UUID,
    @field:Size(max = MESSAGE_BODY_MAX_LENGTH)
    val body: String,
    val attachmentIds: List<UUID>,
)

data class MessageResponse(
    val id: UUID,
    val dialogId: UUID,
    val seq: Long,
    val senderUserId: UUID,
    val clientMessageId: UUID,
    val body: String,
    val createdAt: Instant,
    val attachments: List<MediaFileResponse>,
)

data class DialogResponse(
    val id: UUID,
    val coachId: UUID,
    val clientUserId: UUID,
    val peerUserId: UUID,
    val peerDisplayName: String,
    val lastMessageSeq: Long,
    val readSeq: Long,
    val peerReadSeq: Long,
    val unreadCount: Long,
    val lastMessagePreview: String?,
    val lastMessageAt: Instant?,
)

data class MarkReadRequest(val readSeq: Long)

data class AttachmentDownloadUrlResponse(val downloadUrl: String)
