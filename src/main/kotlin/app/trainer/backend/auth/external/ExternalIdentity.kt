package app.trainer.backend.auth.external

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

enum class ExternalProvider { YANDEX, VK, APPLE, GOOGLE }

data class VerifiedIdentity(
    val provider: ExternalProvider,
    val subject: String,
    val displayName: String?,
)

interface ExternalIdentityVerifier {

    val provider: ExternalProvider

    fun verify(token: String): VerifiedIdentity
}

@Entity
@Table(name = "external_identities")
class ExternalIdentityEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    val provider: ExternalProvider,

    @Column(name = "subject_hash")
    val subjectHash: String,

    @Column(name = "created_at")
    val createdAt: Instant,
)

interface ExternalIdentityRepository : JpaRepository<ExternalIdentityEntity, UUID> {

    fun findByProviderAndSubjectHash(provider: ExternalProvider, subjectHash: String): ExternalIdentityEntity?

    fun findByUserId(userId: UUID): List<ExternalIdentityEntity>

    fun findByUserIdAndProvider(userId: UUID, provider: ExternalProvider): ExternalIdentityEntity?
}
