package app.trainer.backend.chat

import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.media.MediaFileResponse
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.media.MediaOwnerKind
import app.trainer.backend.media.PrepareUploadRequest
import app.trainer.backend.media.PrepareUploadResponse
import app.trainer.backend.push.PushPayload
import app.trainer.backend.push.PushSender
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Limit
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val HISTORY_PAGE_SIZE = 50
private const val PUSH_DIALOG_ID_KEY = "dialogId"
private const val ATTACHMENT_PUSH_PREVIEW = "Вложение"

@Service
class ChatService(
    private val dialogRepository: DialogRepository,
    private val messageRepository: MessageRepository,
    private val dialogReadRepository: DialogReadRepository,
    private val coachRepository: CoachRepository,
    private val userRepository: UserRepository,
    private val broadcaster: MessageBroadcaster,
    private val mediaFileService: MediaFileService,
    private val pushSender: PushSender,
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

        val dialog = dialogRepository.findWithLockById(dialogId) ?: dialogNotFound()
        requireParticipant(dialog = dialog, userId = senderUserId)

        dialog.lastMessageSeq += 1
        val message = MessageEntity(
            id = UUID.randomUUID(),
            dialogId = dialogId,
            seq = dialog.lastMessageSeq,
            senderUserId = senderUserId,
            clientMessageId = request.clientMessageId,
            body = request.body,
            createdAt = Instant.now(clock),
        )
        messageRepository.save(message)
        val attachments = mediaFileService.link(
            mediaFileIds = request.attachmentIds,
            ownerKind = MediaOwnerKind.DIALOG_MESSAGE,
            ownerId = message.id,
            scopeId = dialogId,
            uploaderUserId = senderUserId,
        )

        val response = toResponse(message = message, attachments = attachments.map(mediaFileService::toResponse))
        val recipients = participantsOf(dialog) - senderUserId
        val delivered = broadcaster.broadcast(recipientUserIds = recipients, message = response)
        notifyOffline(
            offlineUserIds = recipients - delivered,
            senderUserId = senderUserId,
            dialogId = dialogId,
            message = message,
            hasAttachments = attachments.isNotEmpty(),
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
    fun dialogsOf(userId: UUID): List<DialogResponse> {
        val coach = coachRepository.findByUserId(userId)
        val dialogs = if (coach == null) {
            dialogRepository.findByClientUserId(userId)
        } else {
            dialogRepository.findByCoachId(coach.id)
        }
        return dialogs
            .mapNotNull { dialog -> toListResponse(dialog = dialog, userId = userId, viewerIsCoach = coach != null) }
            .sortedByDescending { it.lastMessage?.createdAt ?: Instant.EPOCH }
    }

    private fun toListResponse(
        dialog: DialogEntity,
        userId: UUID,
        viewerIsCoach: Boolean,
    ): DialogResponse? {
        val peerUserId = if (viewerIsCoach) {
            dialog.clientUserId
        } else {
            coachRepository.findByIdOrNull(dialog.coachId)?.userId ?: return null
        }
        val peer = userRepository.findByIdOrNull(peerUserId) ?: return null
        val readSeq = dialogReadRepository
            .findByDialogIdAndUserId(dialogId = dialog.id, userId = userId)
            ?.readSeq
            ?: 0
        val peerReadSeq = dialogReadRepository
            .findByDialogIdAndUserId(dialogId = dialog.id, userId = peerUserId)
            ?.readSeq
            ?: 0
        val unreadCount = messageRepository.countByDialogIdAndSeqGreaterThanAndSenderUserIdNot(
            dialogId = dialog.id,
            seq = readSeq,
            senderUserId = userId,
        )
        val lastMessage = messageRepository.findFirstByDialogIdOrderBySeqDesc(dialog.id)
        return DialogResponse(
            id = dialog.id,
            coachId = dialog.coachId,
            clientUserId = dialog.clientUserId,
            peerUserId = peer.id,
            peerDisplayName = peer.displayName,
            lastMessageSeq = dialog.lastMessageSeq,
            readSeq = readSeq,
            peerReadSeq = peerReadSeq,
            unreadCount = unreadCount,
            lastMessage = lastMessage?.let { toResponse(message = it) },
        )
    }

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

    private fun notifyOffline(
        offlineUserIds: Set<UUID>,
        senderUserId: UUID,
        dialogId: UUID,
        message: MessageEntity,
        hasAttachments: Boolean,
    ) {
        if (offlineUserIds.isEmpty()) return
        val senderName = userRepository.findByIdOrNull(senderUserId)?.displayName.orEmpty()
        val preview = when {
            message.body.isNotBlank() -> message.body
            hasAttachments -> ATTACHMENT_PUSH_PREVIEW
            else -> return
        }
        pushSender.send(
            userIds = offlineUserIds,
            payload = PushPayload(
                title = senderName,
                body = preview,
                data = mapOf(PUSH_DIALOG_ID_KEY to dialogId.toString()),
            ),
        )
    }

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
