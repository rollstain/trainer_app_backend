package app.trainer.backend.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service

private const val REFRESH_TOKEN_BYTES = 32

@Service
class TokenService(
    private val jwtEncoder: JwtEncoder,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    private val random = SecureRandom()

    fun issueAccessToken(userId: UUID): AccessToken {
        val issuedAt = Instant.now(clock)
        val expiresAt = issuedAt.plus(properties.accessTokenTtlMinutes, ChronoUnit.MINUTES)
        val claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return AccessToken(value = token, expiresAt = expiresAt)
    }

    fun generateRefreshToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    data class AccessToken(val value: String, val expiresAt: Instant)
}
