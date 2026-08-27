package app.trainer.backend.admin

import app.trainer.backend.auth.AuthProperties
import jakarta.validation.Valid
import java.security.MessageDigest
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val ADMIN_TOKEN_HEADER = "X-Admin-Token"
private const val ADMIN_TOKEN_MIN_LENGTH = 32

@RestController
@RequestMapping("/admin")
class AdminController(
    private val adminService: AdminService,
    private val properties: AuthProperties,
) {

    @PostMapping("/coaches")
    fun onboardCoach(
        @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) token: String?,
        @Valid @RequestBody request: CreateCoachRequest,
    ): CoachOnboardedResponse {
        authorize(token)
        return adminService.onboardCoach(request)
    }

    @PostMapping("/coaches/{coachId}/login-code")
    fun issueLoginCode(
        @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) token: String?,
        @PathVariable coachId: UUID,
    ): LoginCodeResponse {
        authorize(token)
        return adminService.issueLoginCode(coachId)
    }

    private fun authorize(token: String?) {
        val configured = properties.adminToken
        if (configured.length < ADMIN_TOKEN_MIN_LENGTH) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Админ-доступ не настроен")
        }
        val matches = MessageDigest.isEqual(
            token.orEmpty().toByteArray(Charsets.UTF_8),
            configured.toByteArray(Charsets.UTF_8),
        )
        if (!matches) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный админ-токен")
        }
    }
}
