package app.trainer.backend.auth.external

import app.trainer.backend.auth.AuthTokensResponse
import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.security.MessageDigest
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val BOT_SECRET_HEADER = "X-Telegram-Bot-Secret"
private const val BOT_SECRET_MIN_LENGTH = 16

@RestController
class ExternalAuthController(
    private val externalAuthService: ExternalAuthService,
    private val telegramLoginService: TelegramLoginService,
    private val telegramProperties: TelegramProperties,
) {

    @GetMapping("/auth/providers")
    fun availableProviders(): List<ExternalProvider> = buildList {
        if (telegramProperties.botUsername.isNotBlank()) add(ExternalProvider.TELEGRAM)
    }

    @PostMapping("/auth/telegram/start")
    fun startTelegramLogin(): TelegramStartResponse = telegramLoginService.start()

    @PostMapping("/auth/telegram/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirmTelegramLogin(
        @RequestHeader(name = BOT_SECRET_HEADER, required = false) secret: String?,
        @Valid @RequestBody request: TelegramConfirmRequest,
    ) {
        authorizeBot(secret)
        val confirmed = telegramLoginService.confirm(
            startCode = request.startCode,
            telegramUserId = request.telegramUserId,
            telegramDisplayName = request.telegramDisplayName,
        )
        if (!confirmed) {
            throw ResponseStatusException(HttpStatus.GONE, "Ссылка входа уже недействительна")
        }
    }

    @PostMapping("/auth/external")
    fun signIn(@Valid @RequestBody request: ExternalSignInRequest): AuthTokensResponse {
        return externalAuthService.signIn(request)
    }

    @GetMapping("/me/identities")
    fun identities(@CurrentUserId userId: UUID): List<LinkedIdentityResponse> {
        return externalAuthService.linkedIdentities(userId)
    }

    @PostMapping("/me/identities")
    fun link(
        @CurrentUserId userId: UUID,
        @Valid @RequestBody request: LinkIdentityRequest,
    ): List<LinkedIdentityResponse> {
        return externalAuthService.link(userId = userId, request = request)
    }

    @DeleteMapping("/me/identities/{provider}")
    fun unlink(
        @CurrentUserId userId: UUID,
        @PathVariable provider: ExternalProvider,
    ): List<LinkedIdentityResponse> {
        return externalAuthService.unlink(userId = userId, provider = provider)
    }

    private fun authorizeBot(secret: String?) {
        val configured = telegramProperties.botSecret
        if (configured.length < BOT_SECRET_MIN_LENGTH) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Бот не настроен")
        }
        val matches = MessageDigest.isEqual(
            secret.orEmpty().toByteArray(Charsets.UTF_8),
            configured.toByteArray(Charsets.UTF_8),
        )
        if (!matches) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Бот не опознан")
        }
    }
}
