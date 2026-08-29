package app.trainer.backend.auth.password

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "password_credentials")
class PasswordCredentialEntity(

    @Id
    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "password_hash")
    var passwordHash: String,

    @Column(name = "failed_attempts")
    var failedAttempts: Int,

    @Column(name = "locked_until")
    var lockedUntil: Instant?,

    @Column(name = "lock_streak")
    var lockStreak: Int,

    @Column(name = "updated_at")
    var updatedAt: Instant,
)

interface PasswordCredentialRepository : JpaRepository<PasswordCredentialEntity, UUID>
