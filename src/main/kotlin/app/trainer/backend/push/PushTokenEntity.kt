package app.trainer.backend.push

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.annotation.Transactional

enum class PushPlatform { ANDROID, IOS }

@Entity
@Table(name = "push_tokens")
class PushTokenEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "user_id")
    var userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "platform")
    var platform: PushPlatform,

    @Column(name = "token")
    val token: String,

    @Column(name = "locale")
    var locale: String?,

    @Column(name = "updated_at")
    var updatedAt: Instant,
)

interface PushTokenRepository : JpaRepository<PushTokenEntity, UUID> {

    fun findByToken(token: String): PushTokenEntity?

    fun findByUserIdIn(userIds: Collection<UUID>): List<PushTokenEntity>

    @Transactional
    fun deleteByToken(token: String)
}
