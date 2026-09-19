package app.trainer.backend.auth.external

import app.trainer.backend.auth.AuthTokensResponder
import app.trainer.backend.auth.AuthTokensResponse
import app.trainer.backend.config.CurrentUserId
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class ExternalAuthController(
    private val externalAuthService: ExternalAuthService,
    private val telegramLoginService: TelegramLoginService,
    private val botGuard: TelegramBotGuard,
    private val authTokensResponder: AuthTokensResponder,
) {

    @PostMapping("/auth/telegram/start")
    fun startTelegramLogin(): TelegramStartResponse = telegramLoginService.start()

    @PostMapping("/auth/telegram/confirm")
    fun confirmTelegramLogin(
        @RequestHeader(name = TELEGRAM_BOT_SECRET_HEADER, required = false) secret: String?,
        @Valid @RequestBody request: TelegramConfirmRequest,
    ): TelegramConfirmResponse {
        botGuard.authorize(secret)
        val confirmed = telegramLoginService.confirm(
            startCode = request.startCode,
            telegramUserId = request.telegramUserId,
            telegramDisplayName = request.telegramDisplayName,
            telegramUsername = request.telegramUsername,
        ) ?: throw ResponseStatusException(HttpStatus.GONE, "Ссылка входа уже недействительна")

        val targetUserId = confirmed.targetUserId
        val identity = confirmed.identity
        if (targetUserId == null || identity == null) {
            return TelegramConfirmResponse(kind = TelegramConfirmKind.LOGIN)
        }
        externalAuthService.claimVerified(userId = targetUserId, verified = identity)
        return TelegramConfirmResponse(kind = TelegramConfirmKind.LINK)
    }

    @PostMapping("/auth/external")
    fun signIn(
        @Valid @RequestBody request: ExternalSignInRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthTokensResponse {
        val tokens = externalAuthService.signIn(request)
        return authTokensResponder.respond(tokens = tokens, request = httpRequest, response = httpResponse)
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
}
