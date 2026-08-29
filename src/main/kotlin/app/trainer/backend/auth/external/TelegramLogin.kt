package app.trainer.backend.auth.external

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "telegram_logins")
class TelegramLoginEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "start_code")
    val startCode: String,

    @Column(name = "claim_token_hash")
    val claimTokenHash: String,

    @Column(name = "telegram_user_id")
    var telegramUserId: String?,

    @Column(name = "telegram_display_name")
    var telegramDisplayName: String?,

    @Column(name = "telegram_username")
    var telegramUsername: String?,

    @Column(name = "target_user_id")
    val targetUserId: UUID?,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "confirmed_at")
    var confirmedAt: Instant?,

    @Column(name = "consumed_at")
    var consumedAt: Instant?,
)

interface TelegramLoginRepository : JpaRepository<TelegramLoginEntity, UUID> {

    fun findByStartCode(startCode: String): TelegramLoginEntity?

    fun findByClaimTokenHash(claimTokenHash: String): TelegramLoginEntity?

    fun deleteByTargetUserIdIsNullAndCreatedAtBefore(moment: Instant)

    fun deleteByTargetUserIdIsNotNullAndCreatedAtBefore(moment: Instant)
}
