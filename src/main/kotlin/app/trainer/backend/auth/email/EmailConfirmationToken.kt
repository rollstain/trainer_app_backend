package app.trainer.backend.auth.email

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "email_confirmation_tokens")
class EmailConfirmationTokenEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "email")
    val email: String,

    @Column(name = "token_hash")
    val tokenHash: String,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "expires_at")
    val expiresAt: Instant,

    @Column(name = "consumed_at")
    var consumedAt: Instant?,
)

interface EmailConfirmationTokenRepository : JpaRepository<EmailConfirmationTokenEntity, UUID> {

    fun findByTokenHash(tokenHash: String): EmailConfirmationTokenEntity?

    fun findByUserIdAndConsumedAtIsNull(userId: UUID): List<EmailConfirmationTokenEntity>
}
