package app.trainer.backend.chat

import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.config.EXTRA_ROW_TO_DETECT_NEXT_PAGE
import app.trainer.backend.config.Page
import app.trainer.backend.config.PageCursor
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.config.encodeCursor
import app.trainer.backend.config.pageSizeOf
import app.trainer.backend.media.MediaFileResponse
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.media.MediaOwnerKind
import app.trainer.backend.media.PrepareUploadRequest
import app.trainer.backend.media.PrepareUploadResponse
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushText
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Limit
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val HISTORY_PAGE_SIZE = 50
private const val DIALOGS_PER_PAGE = 30
private const val PUSH_BODY_MAX_LENGTH = 120
private const val PUSH_DIALOG_ID_KEY = "dialogId"

@Service
class ChatService(
    private val dialogRepository: DialogRepository,
    private val messageRepository: MessageRepository,
    private val dialogReadRepository: DialogReadRepository,
    private val coachRepository: CoachRepository,
    private val userRepository: UserRepository,
    private val mediaFileService: MediaFileService,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {

    @Transactional
    fun sendMessage(senderUserId: UUID, dialogId: UUID, request: SendMessageRequest): MessageResponse {
        val alreadySent = messageRepository.findByDialogIdAndClientMessageId(
            dialogId = dialogId,
            clientMessageId = request.clientMessageId,
        )
        if (alreadySent != null) return toResponse(alreadySent)
        if (request.body.isBlank() && request.attachmentIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Пустое сообщение без вложений")
        }

        requireDialogAccess(dialogId = dialogId, userId = senderUserId)
        val messageId = UUID.randomUUID()
        val attachments = mediaFileService.link(
            mediaFileIds = request.attachmentIds,
            ownerKind = MediaOwnerKind.DIALOG_MESSAGE,
            ownerId = messageId,
            scopeId = dialogId,
            uploaderUserId = senderUserId,
        )

        val dialog = dialogRepository.findWithLockById(dialogId) ?: dialogNotFound()
        dialog.lastMessageSeq += 1
        val message = MessageEntity(
            id = messageId,
            dialogId = dialogId,
            seq = dialog.lastMessageSeq,
            senderUserId = senderUserId,
            clientMessageId = request.clientMessageId,
            body = request.body,
            createdAt = Instant.now(clock),
        )
        messageRepository.save(message)

        val response = toResponse(message = message, attachments = attachments.map(mediaFileService::toResponse))
        eventPublisher.publishEvent(
            MessageSentEvent(
                message = response,
                recipientUserIds = participantsOf(dialog) - senderUserId,
                push = newMessagePush(
                    dialogId = dialogId,
                    senderDisplayName = userRepository.findByIdOrNull(senderUserId)?.displayName.orEmpty(),
                    body = request.body,
                ),
            )
        )
        return response
    }

    @Transactional(readOnly = true)
    fun historyBefore(userId: UUID, dialogId: UUID, beforeSeq: Long?): List<MessageResponse> {
        val dialog = requireDialogAccess(dialogId = dialogId, userId = userId)
        return messageRepository.findByDialogIdAndSeqLessThanOrderBySeqDesc(
            dialogId = dialog.id,
            seq = beforeSeq ?: Long.MAX_VALUE,
            limit = Limit.of(HISTORY_PAGE_SIZE),
        ).let(::toResponses)
    }

    @Transactional(readOnly = true)
    fun messagesAfter(userId: UUID, dialogId: UUID, afterSeq: Long): List<MessageResponse> {
        val dialog = requireDialogAccess(dialogId = dialogId, userId = userId)
        return messageRepository.findByDialogIdAndSeqGreaterThanOrderBySeqAsc(
            dialogId = dialog.id,
            seq = afterSeq,
            limit = Limit.of(HISTORY_PAGE_SIZE),
        ).let(::toResponses)
    }

    @Transactional
    fun prepareAttachmentUpload(
        userId: UUID,
        dialogId: UUID,
        request: PrepareUploadRequest,
    ): PrepareUploadResponse {
        requireDialogAccess(dialogId = dialogId, userId = userId)
        return mediaFileService.prepareUpload(
            uploaderUserId = userId,
            ownerKind = MediaOwnerKind.DIALOG_MESSAGE,
            scopeId = dialogId,
            request = request,
        )
    }

    @Transactional(readOnly = true)
    fun attachmentDownloadUrl(userId: UUID, dialogId: UUID, attachmentId: UUID): AttachmentDownloadUrlResponse {
        requireDialogAccess(dialogId = dialogId, userId = userId)
        return AttachmentDownloadUrlResponse(
            downloadUrl = mediaFileService.freshDownloadUrl(
                mediaFileId = attachmentId,
                ownerKind = MediaOwnerKind.DIALOG_MESSAGE,
                scopeId = dialogId,
            ),
        )
    }

    @Transactional
    fun markRead(userId: UUID, dialogId: UUID, readSeq: Long) {
        requireDialogAccess(dialogId = dialogId, userId = userId)
        val existing = dialogReadRepository.findByDialogIdAndUserId(dialogId = dialogId, userId = userId)
        if (existing == null) {
            dialogReadRepository.save(
                DialogReadEntity(
                    dialogId = dialogId,
                    userId = userId,
                    readSeq = readSeq,
                    updatedAt = Instant.now(clock),
                )
            )
            return
        }
        if (readSeq <= existing.readSeq) return
        existing.readSeq = readSeq
        existing.updatedAt = Instant.now(clock)
    }

    @Transactional(readOnly = true)
    fun dialogsOf(userId: UUID, limit: Int?, after: String?): Page<DialogResponse> {
        val pageSize = pageSizeOf(limit) ?: DIALOGS_PER_PAGE
        val cursor = decodeCursor(after)
        val fetched = dialogRepository.findPage(
            viewerUserId = userId,
            afterSortKey = cursor?.sortKey,
            afterId = cursor?.id,
            pageSize = pageSize + EXTRA_ROW_TO_DETECT_NEXT_PAGE,
        )
        val rows = fetched.take(pageSize)
        val hasMore = fetched.size > pageSize
        val last = rows.lastOrNull()?.takeIf { hasMore }
        return Page(
            items = rows.map(::toListResponse),
            nextCursor = last?.let {
                encodeCursor(PageCursor(sortKey = it.getSortKey().toString(), id = it.getDialogId()))
            },
        )
    }

    private fun toListResponse(row: DialogListRow): DialogResponse = DialogResponse(
        id = row.getDialogId(),
        coachId = row.getCoachId(),
        clientUserId = row.getClientUserId(),
        peerUserId = row.getPeerUserId(),
        peerDisplayName = row.getPeerDisplayName(),
        lastMessageSeq = row.getLastMessageSeq(),
        readSeq = row.getReadSeq(),
        peerReadSeq = row.getPeerReadSeq(),
        unreadCount = row.getUnreadCount(),
        lastMessagePreview = row.getMessageBody(),
        lastMessageAt = row.getMessageCreatedAt(),
    )

    @Transactional
    fun openDialog(coachId: UUID, clientUserId: UUID): DialogEntity {
        val existing = dialogRepository.findByCoachIdAndClientUserId(
            coachId = coachId,
            clientUserId = clientUserId,
        )
        if (existing != null) return existing
        return dialogRepository.save(
            DialogEntity(
                id = UUID.randomUUID(),
                coachId = coachId,
                clientUserId = clientUserId,
                lastMessageSeq = 0,
                createdAt = Instant.now(clock),
            )
        )
    }

    private fun requireDialogAccess(dialogId: UUID, userId: UUID): DialogEntity {
        val dialog = dialogRepository.findByIdOrNull(dialogId) ?: dialogNotFound()
        requireParticipant(dialog = dialog, userId = userId)
        return dialog
    }

    private fun requireParticipant(dialog: DialogEntity, userId: UUID) {
        if (userId !in participantsOf(dialog)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не участник диалога")
        }
    }

    private fun participantsOf(dialog: DialogEntity): Set<UUID> {
        val coach = coachRepository.findByIdOrNull(dialog.coachId) ?: dialogNotFound()
        return setOf(coach.userId, dialog.clientUserId)
    }

    private fun newMessagePush(
        dialogId: UUID,
        senderDisplayName: String,
        body: String?,
    ): PushMessage = PushMessage(
        channel = PushChannel.CHAT,
        text = PushText.NEW_CHAT_MESSAGE,
        args = listOf(senderDisplayName, body.orEmpty().take(PUSH_BODY_MAX_LENGTH)),
        data = mapOf(PUSH_DIALOG_ID_KEY to dialogId.toString()),
    )

    private fun dialogNotFound(): Nothing {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Диалог не найден")
    }

    private fun toResponse(
        message: MessageEntity,
        attachments: List<MediaFileResponse> = emptyList(),
    ): MessageResponse = MessageResponse(
        id = message.id,
        dialogId = message.dialogId,
        seq = message.seq,
        senderUserId = message.senderUserId,
        clientMessageId = message.clientMessageId,
        body = message.body,
        createdAt = message.createdAt,
        attachments = attachments,
    )

    private fun toResponses(messages: List<MessageEntity>): List<MessageResponse> {
        val attachmentsByMessage = mediaFileService.filesOf(
            ownerKind = MediaOwnerKind.DIALOG_MESSAGE,
            ownerIds = messages.map { it.id },
        )
        return messages.map { message ->
            toResponse(message = message, attachments = attachmentsByMessage[message.id].orEmpty())
        }
    }
}
