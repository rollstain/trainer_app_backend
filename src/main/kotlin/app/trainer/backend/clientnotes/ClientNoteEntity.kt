package app.trainer.backend.clientnotes

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class ClientNoteKind { MEDICAL, GENERAL }

@Entity
@Table(name = "client_notes")
class ClientNoteEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "client_user_id")
    val clientUserId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    var kind: ClientNoteKind,

    @Column(name = "title")
    var title: String,

    @Column(name = "details")
    var details: String?,

    @Column(name = "is_pinned")
    var isPinned: Boolean,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "updated_at")
    var updatedAt: Instant,

    @Column(name = "archived_at")
    var archivedAt: Instant?,
)
