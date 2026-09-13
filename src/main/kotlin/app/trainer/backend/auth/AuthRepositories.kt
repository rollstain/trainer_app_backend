package app.trainer.backend.auth

import jakarta.persistence.LockModeType
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock

interface InviteRepository : JpaRepository<InviteEntity, UUID> {

    fun findByCode(code: String): InviteEntity?

    fun findByCoachId(coachId: UUID): List<InviteEntity>
}

interface DeviceSessionRepository : JpaRepository<DeviceSessionEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByRefreshTokenHash(refreshTokenHash: String): DeviceSessionEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByPreviousRefreshTokenHash(previousRefreshTokenHash: String): DeviceSessionEntity?

    fun findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId: UUID): List<DeviceSessionEntity>

    fun findByUserId(userId: UUID): List<DeviceSessionEntity>
}
