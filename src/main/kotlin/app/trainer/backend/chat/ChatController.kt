package app.trainer.backend.chat

import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.config.pageResponse
import app.trainer.backend.media.PrepareUploadRequest
import app.trainer.backend.media.PrepareUploadResponse
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/dialogs")
class ChatController(private val chatService: ChatService) {

    @GetMapping
    fun dialogs(
        @CurrentUserId userId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) after: String?,
    ): ResponseEntity<List<DialogResponse>> {
        return pageResponse(chatService.dialogsOf(userId = userId, limit = limit, after = after))
    }

    @GetMapping("/{dialogId}/messages")
    fun history(
        @CurrentUserId userId: UUID,
        @PathVariable dialogId: UUID,
        @RequestParam(required = false) beforeSeq: Long?,
    ): List<MessageResponse> {
        return chatService.historyBefore(userId = userId, dialogId = dialogId, beforeSeq = beforeSeq)
    }

    @GetMapping("/{dialogId}/messages/after")
    fun sync(
        @CurrentUserId userId: UUID,
        @PathVariable dialogId: UUID,
        @RequestParam afterSeq: Long,
    ): List<MessageResponse> {
        return chatService.messagesAfter(userId = userId, dialogId = dialogId, afterSeq = afterSeq)
    }

    @PostMapping("/{dialogId}/messages")
    fun send(
        @CurrentUserId userId: UUID,
        @PathVariable dialogId: UUID,
        @Valid @RequestBody request: SendMessageRequest,
    ): MessageResponse {
        return chatService.sendMessage(senderUserId = userId, dialogId = dialogId, request = request)
    }

    @PostMapping("/{dialogId}/attachments")
    fun prepareUpload(
        @CurrentUserId userId: UUID,
        @PathVariable dialogId: UUID,
        @Valid @RequestBody request: PrepareUploadRequest,
    ): PrepareUploadResponse {
        return chatService.prepareAttachmentUpload(
            userId = userId,
            dialogId = dialogId,
            request = request,
        )
    }

    @GetMapping("/{dialogId}/attachments/{attachmentId}/download-url")
    fun attachmentDownloadUrl(
        @CurrentUserId userId: UUID,
        @PathVariable dialogId: UUID,
        @PathVariable attachmentId: UUID,
    ): AttachmentDownloadUrlResponse {
        return chatService.attachmentDownloadUrl(
            userId = userId,
            dialogId = dialogId,
            attachmentId = attachmentId,
        )
    }

    @PostMapping("/{dialogId}/read")
    fun markRead(
        @CurrentUserId userId: UUID,
        @PathVariable dialogId: UUID,
        @RequestBody request: MarkReadRequest,
    ) {
        chatService.markRead(userId = userId, dialogId = dialogId, readSeq = request.readSeq)
    }
}
