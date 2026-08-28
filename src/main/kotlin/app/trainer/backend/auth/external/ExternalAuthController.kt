package app.trainer.backend.auth.external

import app.trainer.backend.auth.AuthTokensResponse
import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class ExternalAuthController(private val externalAuthService: ExternalAuthService) {

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
}
