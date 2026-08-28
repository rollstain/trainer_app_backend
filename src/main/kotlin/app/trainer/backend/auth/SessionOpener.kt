package app.trainer.backend.auth

import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SessionOpener(
    private val deviceSessionRepository: DeviceSessionRepository,
    private val tokenService: TokenService,
    private val clock: Clock,
) {

    @Transactional
    fun openSession(userId: UUID, deviceInfo: String): AuthTokensResponse {
        val now = Instant.now(clock)
        val refreshToken = tokenService.generateRefreshToken()
        val session = deviceSessionRepository.save(
            DeviceSessionEntity(
                id = UUID.randomUUID(),
                userId = userId,
                refreshTokenHash = tokenService.hash(refreshToken),
                previousRefreshTokenHash = null,
                rotatedAt = null,
                deviceInfo = deviceInfo,
                createdAt = now,
                lastSeenAt = now,
                revokedAt = null,
            )
        )
        val accessToken = tokenService.issueAccessToken(userId = userId, sessionId = session.id)
        return AuthTokensResponse(
            accessToken = accessToken.value,
            refreshToken = refreshToken,
            accessTokenExpiresAt = accessToken.expiresAt,
        )
    }
}
