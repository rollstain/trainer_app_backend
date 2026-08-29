package app.trainer.backend.auth.password

import app.trainer.backend.auth.AuthTokensResponse
import app.trainer.backend.config.CurrentSessionId
import app.trainer.backend.config.CurrentUserId
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
) {

    @PostMapping("/auth/password/sign-up")
    fun signUp(@Valid @RequestBody request: PasswordSignUpRequest): AuthTokensResponse {
        return passwordAuthService.signUp(request)
    }

    @PostMapping("/auth/password/sign-in")
    fun signIn(@Valid @RequestBody request: PasswordSignInRequest): AuthTokensResponse {
        return passwordAuthService.signIn(request)
    }

    @PostMapping("/auth/password/reset/telegram")
    fun resetByTelegram(@Valid @RequestBody request: PasswordResetRequest): AuthTokensResponse {
        return passwordResetService.resetByTelegram(request)
    }

    @PostMapping("/auth/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forgot(@Valid @RequestBody request: ForgotPasswordRequest) {
        passwordResetService.requestReset(request)
    }

    @PostMapping("/auth/password/reset/email")
    fun resetByEmail(@Valid @RequestBody request: PasswordResetByEmailRequest): AuthTokensResponse {
        return passwordResetService.resetByEmail(request)
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
