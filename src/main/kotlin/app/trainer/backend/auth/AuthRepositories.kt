package app.trainer.backend.auth

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface InviteRepository : JpaRepository<InviteEntity, UUID> {

    fun findByCode(code: String): InviteEntity?

    fun findByCoachId(coachId: UUID): List<InviteEntity>
}

interface DeviceSessionRepository : JpaRepository<DeviceSessionEntity, UUID> {

    fun findByRefreshTokenHash(refreshTokenHash: String): DeviceSessionEntity?

    fun findByPreviousRefreshTokenHash(previousRefreshTokenHash: String): DeviceSessionEntity?

    fun findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId: UUID): List<DeviceSessionEntity>

    fun findByUserId(userId: UUID): List<DeviceSessionEntity>
}
