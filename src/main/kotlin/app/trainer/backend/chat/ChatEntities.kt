package app.trainer.backend.chat

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "dialogs")
class DialogEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "client_user_id")
    val clientUserId: UUID,

    @Column(name = "last_message_seq")
    var lastMessageSeq: Long,

    @Column(name = "created_at")
    val createdAt: Instant,
)

@Entity
@Table(name = "messages")
class MessageEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "dialog_id")
    val dialogId: UUID,

    @Column(name = "seq")
    val seq: Long,

    @Column(name = "sender_user_id")
    val senderUserId: UUID,

    @Column(name = "client_message_id")
    val clientMessageId: UUID,

    @Column(name = "body")
    val body: String,

    @Column(name = "created_at")
    val createdAt: Instant,
)

class DialogReadId : Serializable {
    var dialogId: UUID? = null
    var userId: UUID? = null
}

@Entity
@Table(name = "dialog_reads")
@IdClass(DialogReadId::class)
class DialogReadEntity(

    @Id
    @Column(name = "dialog_id")
    val dialogId: UUID,

    @Id
    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "read_seq")
    var readSeq: Long,

    @Column(name = "updated_at")
    var updatedAt: Instant,
)
