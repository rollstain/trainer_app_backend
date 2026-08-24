package app.trainer.backend.auth

import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/invites")
    fun createInvite(@CurrentUserId coachUserId: UUID): InviteResponse {
        return authService.createInvite(coachUserId = coachUserId)
    }

    @PostMapping("/invites/redeem")
    fun redeemInvite(@Valid @RequestBody request: RedeemInviteRequest): AuthTokensResponse {
        return authService.redeemInvite(request = request)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): AuthTokensResponse {
        return authService.refresh(request = request)
    }
}
