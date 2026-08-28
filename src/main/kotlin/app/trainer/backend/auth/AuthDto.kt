package app.trainer.backend.auth

import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class InviteResponse(
    val code: String,
    val expiresAt: Instant,
)

data class InvitePreviewResponse(
    val coachDisplayName: String,
    val expiresAt: Instant,
    val needsDisplayName: Boolean,
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

data class DeviceSessionResponse(
    val id: UUID,
    val deviceInfo: String,
    val createdAt: Instant,
    val lastSeenAt: Instant,
    val isCurrent: Boolean,
)

data class JoinCoachRequest(
    @field:NotBlank
    val code: String,
)
