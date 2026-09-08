package app.trainer.backend.auth.password

import app.trainer.backend.auth.AuthTokensResponder
import app.trainer.backend.auth.AuthTokensResponse
import app.trainer.backend.config.CurrentSessionId
import app.trainer.backend.config.CurrentUserId
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class PasswordAuthController(
    private val passwordAuthService: PasswordAuthService,
    private val passwordResetService: PasswordResetService,
    private val authTokensResponder: AuthTokensResponder,
) {

    @PostMapping("/auth/password/sign-up")
    fun signUp(
        @Valid @RequestBody request: PasswordSignUpRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthTokensResponse {
        val tokens = passwordAuthService.signUp(request)
        return authTokensResponder.respond(tokens = tokens, request = httpRequest, response = httpResponse)
    }

    @PostMapping("/auth/password/sign-in")
    fun signIn(
        @Valid @RequestBody request: PasswordSignInRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthTokensResponse {
        val tokens = passwordAuthService.signIn(request)
        return authTokensResponder.respond(tokens = tokens, request = httpRequest, response = httpResponse)
    }

    @PostMapping("/auth/password/reset/telegram")
    fun resetByTelegram(
        @Valid @RequestBody request: PasswordResetRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthTokensResponse {
        val tokens = passwordResetService.resetByTelegram(request)
        return authTokensResponder.respond(tokens = tokens, request = httpRequest, response = httpResponse)
    }

    @PostMapping("/auth/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forgot(@Valid @RequestBody request: ForgotPasswordRequest) {
        passwordResetService.requestReset(request)
    }

    @PostMapping("/auth/password/reset/email")
    fun resetByEmail(
        @Valid @RequestBody request: PasswordResetByEmailRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthTokensResponse {
        val tokens = passwordResetService.resetByEmail(request)
        return authTokensResponder.respond(tokens = tokens, request = httpRequest, response = httpResponse)
    }

    @PutMapping("/me/password")
    fun setPassword(
        @CurrentUserId userId: UUID,
        @CurrentSessionId sessionId: UUID?,
        @Valid @RequestBody request: SetPasswordRequest,
    ) {
        passwordAuthService.setPassword(userId = userId, currentSessionId = sessionId, request = request)
    }
}
