package app.trainer.backend.auth.external

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

private const val SUBJECT_SEPARATOR = ":"

enum class ExternalProvider { YANDEX, VK, APPLE, GOOGLE, TELEGRAM }

fun subjectHashOf(verified: VerifiedIdentity): String {
    val source = verified.provider.name + SUBJECT_SEPARATOR + verified.subject
    val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

data class VerifiedIdentity(
    val provider: ExternalProvider,
    val subject: String,
    val displayName: String?,
    val username: String?,
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

    @Column(name = "username")
    var username: String?,

    @Column(name = "created_at")
    val createdAt: Instant,
)

interface ExternalIdentityRepository : JpaRepository<ExternalIdentityEntity, UUID> {

    fun findByProviderAndSubjectHash(provider: ExternalProvider, subjectHash: String): ExternalIdentityEntity?

    fun findByUserId(userId: UUID): List<ExternalIdentityEntity>

    fun findByUserIdAndProvider(userId: UUID, provider: ExternalProvider): ExternalIdentityEntity?
}
