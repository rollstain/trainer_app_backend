package app.trainer.backend.chat

import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DialogListRow {

    fun getDialogId(): UUID

    fun getCoachId(): UUID

    fun getClientUserId(): UUID

    fun getPeerUserId(): UUID

    fun getPeerDisplayName(): String

    fun getLastMessageSeq(): Long

    fun getReadSeq(): Long

    fun getPeerReadSeq(): Long

    fun getUnreadCount(): Long

    fun getSortKey(): Instant

    fun getMessageBody(): String?

    fun getMessageCreatedAt(): Instant?
}

interface DialogRepository : JpaRepository<DialogEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: UUID): DialogEntity?

    fun findByCoachIdAndClientUserId(coachId: UUID, clientUserId: UUID): DialogEntity?

    @Query(
        value = """
            select d.id as dialogId,
                   d.coach_id as coachId,
                   d.client_user_id as clientUserId,
                   peer.id as peerUserId,
                   peer.display_name as peerDisplayName,
                   d.last_message_seq as lastMessageSeq,
                   coalesce(viewer_read.read_seq, 0) as readSeq,
                   coalesce(peer_read.read_seq, 0) as peerReadSeq,
                   (
                     select count(*) from messages unread
                     where unread.dialog_id = d.id
                       and unread.seq > coalesce(viewer_read.read_seq, 0)
                       and unread.sender_user_id <> :viewerUserId
                   ) as unreadCount,
                   coalesce(last_message.created_at, timestamp 'epoch') as sortKey,
                   last_message.body as messageBody,
                   last_message.created_at as messageCreatedAt
            from dialogs d
            join coaches c on c.id = d.coach_id
            join users peer
              on peer.id = case when c.user_id = :viewerUserId then d.client_user_id else c.user_id end
            left join dialog_reads viewer_read
              on viewer_read.dialog_id = d.id and viewer_read.user_id = :viewerUserId
            left join dialog_reads peer_read
              on peer_read.dialog_id = d.id and peer_read.user_id = peer.id
            left join lateral (
                select m.* from messages m where m.dialog_id = d.id order by m.seq desc limit 1
            ) last_message on true
            where (c.user_id = :viewerUserId or d.client_user_id = :viewerUserId)
              and (
                cast(:afterSortKey as text) is null
                or (coalesce(last_message.created_at, timestamp 'epoch'), d.id)
                   < (cast(:afterSortKey as timestamptz), cast(:afterId as uuid))
              )
            order by coalesce(last_message.created_at, timestamp 'epoch') desc, d.id desc
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findPage(
        @Param("viewerUserId") viewerUserId: UUID,
        @Param("afterSortKey") afterSortKey: String?,
        @Param("afterId") afterId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<DialogListRow>
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
}

interface DialogReadRepository : JpaRepository<DialogReadEntity, DialogReadId> {

    fun findByDialogIdAndUserId(dialogId: UUID, userId: UUID): DialogReadEntity?

    fun findByDialogId(dialogId: UUID): List<DialogReadEntity>
}
