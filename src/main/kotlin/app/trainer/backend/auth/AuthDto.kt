package app.trainer.backend.auth

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class InviteResponse(
    val code: String,
    val expiresAt: Instant,
)

data class RedeemInviteRequest(
    @field:NotBlank
    val code: String,
    val displayName: String?,
    @field:NotBlank
    val deviceInfo: String,
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class AuthTokensResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Instant,
)
