package app.trainer.backend.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "trainer.auth")
data class AuthProperties(
    val accessTokenTtlMinutes: Long,
    val refreshTokenIdleDays: Long,
    val refreshTokenAbsoluteDays: Long,
    val refreshRotationGraceSeconds: Long,
    val inviteTtlHours: Long,
    val passwordMaxFailedAttempts: Int,
    val passwordLockMinutes: Long,
    val passwordLockMaxMinutes: Long,
    val passwordResetTtlMinutes: Long,
    val passwordResetResendSeconds: Long,
    val jwtSecret: String,
    val adminToken: String,
)
