package app.trainer.backend.auth.external

import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class ExternalSignInRequest(
    val provider: ExternalProvider,
    @field:NotBlank
    val token: String,
    @field:NotBlank
    val deviceInfo: String,
)

data class LinkIdentityRequest(
    val provider: ExternalProvider,
    @field:NotBlank
    val token: String,
)

data class TelegramConfirmRequest(
    @field:NotBlank
    val startCode: String,
    @field:NotBlank
    val telegramUserId: String,
    val telegramDisplayName: String?,
)

data class TelegramConfirmResponse(
    val kind: TelegramConfirmKind,
)

enum class TelegramConfirmKind { LOGIN, LINK }

data class TelegramStartResponse(
    val claimToken: String,
    val deepLink: String,
)

data class LinkedIdentityResponse(
    val provider: ExternalProvider,
    val linkedAt: Instant,
)
