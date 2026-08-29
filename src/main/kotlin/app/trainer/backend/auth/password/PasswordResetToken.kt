package app.trainer.backend.auth.password

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetTokenEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "token_hash")
    val tokenHash: String,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "expires_at")
    val expiresAt: Instant,

    @Column(name = "consumed_at")
    var consumedAt: Instant?,
)

interface PasswordResetTokenRepository : JpaRepository<PasswordResetTokenEntity, UUID> {

    fun findByTokenHash(tokenHash: String): PasswordResetTokenEntity?

    fun findByUserIdAndConsumedAtIsNull(userId: UUID): List<PasswordResetTokenEntity>
}
