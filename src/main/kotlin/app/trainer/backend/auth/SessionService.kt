package app.trainer.backend.auth

import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class SessionService(
    private val deviceSessionRepository: DeviceSessionRepository,
    private val tokenService: TokenService,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    @Transactional
    fun refresh(request: RefreshRequest): AuthTokensResponse {
        val now = Instant.now(clock)
        val incomingHash = tokenService.hash(request.refreshToken)
        val session = sessionByCurrentOrPreviousToken(incomingHash = incomingHash, now = now)
        requireLiveSession(session = session, now = now)
        return rotate(session = session, now = now)
    }

    private fun sessionByCurrentOrPreviousToken(incomingHash: String, now: Instant): DeviceSessionEntity {
        val current = deviceSessionRepository.findByRefreshTokenHash(incomingHash)
        if (current != null) return current

        val rotated = deviceSessionRepository.findByPreviousRefreshTokenHash(incomingHash)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Сессия не найдена")
        val graceEndsAt = rotated.rotatedAt?.plusSeconds(properties.refreshRotationGraceSeconds)
        if (graceEndsAt == null || graceEndsAt.isBefore(now)) {
            revokeChain(session = rotated, now = now)
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Сессия отозвана")
        }
        return rotated
    }

    private fun requireLiveSession(session: DeviceSessionEntity, now: Instant) {
        if (session.revokedAt != null) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Сессия отозвана")
        }
        val idleDeadline = session.lastSeenAt.plus(properties.refreshTokenIdleDays, ChronoUnit.DAYS)
        val absoluteDeadline = session.createdAt.plus(properties.refreshTokenAbsoluteDays, ChronoUnit.DAYS)
        if (idleDeadline.isBefore(now) || absoluteDeadline.isBefore(now)) {
            session.revokedAt = now
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Срок сессии истёк")
        }
    }

    private fun rotate(session: DeviceSessionEntity, now: Instant): AuthTokensResponse {
        val refreshToken = tokenService.generateRefreshToken()
        val replacedHash = session.refreshTokenHash
        session.refreshTokenHash = tokenService.hash(refreshToken)
        session.previousRefreshTokenHash = session.previousRefreshTokenHash ?: replacedHash
        session.rotatedAt = session.rotatedAt.takeIf { withinGrace(it, now) } ?: now
        session.lastSeenAt = now

        val accessToken = tokenService.issueAccessToken(userId = session.userId, sessionId = session.id)
        return AuthTokensResponse(
            accessToken = accessToken.value,
            refreshToken = refreshToken,
            accessTokenExpiresAt = accessToken.expiresAt,
        )
    }

    private fun withinGrace(rotatedAt: Instant?, now: Instant): Boolean {
        val graceEndsAt = rotatedAt?.plusSeconds(properties.refreshRotationGraceSeconds) ?: return false
        return !graceEndsAt.isBefore(now)
    }

    private fun revokeChain(session: DeviceSessionEntity, now: Instant) {
        session.revokedAt = now
        session.previousRefreshTokenHash = null
    }

    @Transactional(readOnly = true)
    fun sessionsOf(userId: UUID, currentSessionId: UUID?): List<DeviceSessionResponse> {
        return deviceSessionRepository
            .findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId)
            .map { session ->
                DeviceSessionResponse(
                    id = session.id,
                    deviceInfo = session.deviceInfo,
                    createdAt = session.createdAt,
                    lastSeenAt = session.lastSeenAt,
                    isCurrent = session.id == currentSessionId,
                )
            }
    }

    @Transactional
    fun revokeSession(userId: UUID, sessionId: UUID) {
        val session = deviceSessionRepository.findByIdOrNull(sessionId)
        if (session == null || session.userId != userId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Сессия не найдена")
        }
        session.revokedAt = Instant.now(clock)
    }

    @Transactional
    fun revokeOtherSessions(userId: UUID, currentSessionId: UUID?) {
        val now = Instant.now(clock)
        deviceSessionRepository
            .findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId)
            .filter { it.id != currentSessionId }
            .forEach { it.revokedAt = now }
    }
}
