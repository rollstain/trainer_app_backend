package app.trainer.backend.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "trainer.auth")
data class AuthProperties(
    val accessTokenTtlMinutes: Long,
    val refreshTokenTtlDays: Long,
    val inviteTtlHours: Long,
    val jwtSecret: String,
)
