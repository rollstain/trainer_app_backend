package app.trainer.backend.auth

import app.trainer.backend.config.CurrentSessionId
import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
    private val sessionService: SessionService,
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
    fun redeemInvite(@Valid @RequestBody request: RedeemInviteRequest): AuthTokensResponse {
        return authService.redeemInvite(request = request)
    }

    @GetMapping("/sessions")
    fun sessions(
        @CurrentUserId userId: UUID,
        @CurrentSessionId sessionId: UUID?,
    ): List<DeviceSessionResponse> {
        return sessionService.sessionsOf(userId = userId, currentSessionId = sessionId)
    }

    @DeleteMapping("/sessions/{sessionId}")
    fun revokeSession(@CurrentUserId userId: UUID, @PathVariable sessionId: UUID) {
        sessionService.revokeSession(userId = userId, sessionId = sessionId)
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
    fun refresh(@Valid @RequestBody request: RefreshRequest): AuthTokensResponse {
        return sessionService.refresh(request = request)
    }
}
