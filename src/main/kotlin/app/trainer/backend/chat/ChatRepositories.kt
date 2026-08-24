package app.trainer.backend.chat

import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface DialogRepository : JpaRepository<DialogEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: UUID): DialogEntity?

    fun findByCoachIdAndClientUserId(coachId: UUID, clientUserId: UUID): DialogEntity?

    fun findByCoachId(coachId: UUID): List<DialogEntity>

    fun findByClientUserId(clientUserId: UUID): List<DialogEntity>
}

interface MessageRepository : JpaRepository<MessageEntity, UUID> {

    fun findByDialogIdAndClientMessageId(dialogId: UUID, clientMessageId: UUID): MessageEntity?

    fun findByDialogIdAndSeqGreaterThanOrderBySeqAsc(
        dialogId: UUID,
        seq: Long,
        limit: Limit,
    ): List<MessageEntity>

    fun findByDialogIdAndSeqLessThanOrderBySeqDesc(
        dialogId: UUID,
        seq: Long,
        limit: Limit,
    ): List<MessageEntity>

    fun findFirstByDialogIdOrderBySeqDesc(dialogId: UUID): MessageEntity?

    fun countByDialogIdAndSeqGreaterThanAndSenderUserIdNot(
        dialogId: UUID,
        seq: Long,
        senderUserId: UUID,
    ): Long
}

interface DialogReadRepository : JpaRepository<DialogReadEntity, DialogReadId> {

    fun findByDialogIdAndUserId(dialogId: UUID, userId: UUID): DialogReadEntity?

    fun findByDialogId(dialogId: UUID): List<DialogReadEntity>
}
