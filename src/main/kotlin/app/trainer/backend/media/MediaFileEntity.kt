package app.trainer.backend.media

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

enum class MediaOwnerKind { DIALOG_MESSAGE, CHECK_IN, EXERCISE, FORM_CHECK }

@Entity
@Table(name = "media_files")
class MediaFileEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_kind")
    val ownerKind: MediaOwnerKind,

    @Column(name = "owner_id")
    var ownerId: UUID?,

    @Column(name = "scope_id")
    val scopeId: UUID,

    @Column(name = "uploaded_by_user_id")
    val uploadedByUserId: UUID,

    @Column(name = "storage_key")
    val storageKey: String,

    @Column(name = "content_type")
    val contentType: String,

    @Column(name = "size_bytes")
    val sizeBytes: Long,

    @Column(name = "original_name")
    val originalName: String,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "linked_at")
    var linkedAt: Instant?,
)

interface MediaFileRepository : JpaRepository<MediaFileEntity, UUID> {

    fun findByOwnerKindAndOwnerIdIn(
        ownerKind: MediaOwnerKind,
        ownerIds: Collection<UUID>,
    ): List<MediaFileEntity>

    fun findByOwnerKindAndOwnerId(ownerKind: MediaOwnerKind, ownerId: UUID): List<MediaFileEntity>

    fun findByOwnerIdIsNullAndCreatedAtBefore(createdAt: Instant): List<MediaFileEntity>
}
