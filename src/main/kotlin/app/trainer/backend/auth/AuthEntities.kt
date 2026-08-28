package app.trainer.backend.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "invites")
class InviteEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "target_user_id")
    val targetUserId: UUID?,

    @Column(name = "code")
    val code: String,

    @Column(name = "expires_at")
    val expiresAt: Instant,

    @Column(name = "used_at")
    var usedAt: Instant?,

    @Column(name = "used_by_user_id")
    var usedByUserId: UUID?,

    @Column(name = "created_at")
    val createdAt: Instant,
)

@Entity
@Table(name = "device_sessions")
class DeviceSessionEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "refresh_token_hash")
    var refreshTokenHash: String,

    @Column(name = "previous_refresh_token_hash")
    var previousRefreshTokenHash: String?,

    @Column(name = "rotated_at")
    var rotatedAt: Instant?,

    @Column(name = "device_info")
    val deviceInfo: String,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "last_seen_at")
    var lastSeenAt: Instant,

    @Column(name = "revoked_at")
    var revokedAt: Instant?,
)
