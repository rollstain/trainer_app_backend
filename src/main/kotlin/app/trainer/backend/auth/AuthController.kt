package app.trainer.backend.auth

import app.trainer.backend.config.CurrentSessionId
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val sessionService: SessionService,
    private val authTokensResponder: AuthTokensResponder,
    private val refreshTokenCookie: RefreshTokenCookie,
) {

    @PostMapping("/invites")
    fun createInvite(@CurrentUserId coachUserId: UUID): InviteResponse {
        return authService.createInvite(coachUserId = coachUserId)
    }

    @GetMapping("/invites/{code}")
    fun previewInvite(@PathVariable code: String): InvitePreviewResponse {
        return authService.previewInvite(code = code)
    }

    @PostMapping("/invites/redeem")
    fun redeemInvite(
        @Valid @RequestBody request: RedeemInviteRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthTokensResponse {
        val tokens = authService.redeemInvite(request = request)
        return authTokensResponder.respond(tokens = tokens, request = httpRequest, response = httpResponse)
    }

    @GetMapping("/sessions")
    fun sessions(
        @CurrentUserId userId: UUID,
        @CurrentSessionId sessionId: UUID?,
    ): List<DeviceSessionResponse> {
        return sessionService.sessionsOf(userId = userId, currentSessionId = sessionId)
    }

    @DeleteMapping("/sessions/{sessionId}")
    fun revokeSession(
        @CurrentUserId userId: UUID,
        @CurrentSessionId currentSessionId: UUID?,
        @PathVariable sessionId: UUID,
        httpResponse: HttpServletResponse,
    ) {
        sessionService.revokeSession(userId = userId, sessionId = sessionId)
        if (sessionId == currentSessionId) {
            refreshTokenCookie.clear(httpResponse)
        }
    }

    @PostMapping("/sessions/revoke-others")
    fun revokeOtherSessions(@CurrentUserId userId: UUID, @CurrentSessionId sessionId: UUID?) {
        sessionService.revokeOtherSessions(userId = userId, currentSessionId = sessionId)
    }

    @PostMapping("/invites/join")
    fun joinCoach(@CurrentUserId userId: UUID, @Valid @RequestBody request: JoinCoachRequest) {
        authService.joinCoachByCode(userId = userId, code = request.code)
    }

    @PostMapping("/refresh")
    fun refresh(
        @RequestBody(required = false) request: RefreshRequest?,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthTokensResponse {
        val refreshToken = request?.refreshToken?.takeIf { it.isNotBlank() }
            ?: refreshTokenCookie.read(httpRequest)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh-токен не передан")
        val tokens = sessionService.refresh(refreshToken = refreshToken)
        return authTokensResponder.respond(tokens = tokens, request = httpRequest, response = httpResponse)
    }
}
