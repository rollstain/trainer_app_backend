package app.trainer.backend.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "trainer.auth")
data class AuthProperties(
    val accessTokenTtlMinutes: Long,
    val refreshTokenIdleDays: Long,
    val refreshTokenAbsoluteDays: Long,
    val refreshRotationGraceSeconds: Long,
    val inviteTtlHours: Long,
    val jwtSecret: String,
    val adminToken: String,
)
