package app.trainer.backend.admin

import app.trainer.backend.auth.AuthProperties
import app.trainer.backend.auth.external.TelegramStartResponse
import app.trainer.backend.coachrequest.ApprovedCoachResponse
import app.trainer.backend.coachrequest.CoachRequestResponse
import app.trainer.backend.coachrequest.CoachRequestService
import jakarta.validation.Valid
import java.security.MessageDigest
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val ADMIN_TOKEN_HEADER = "X-Admin-Token"
private const val ADMIN_TOKEN_MIN_LENGTH = 32

private const val DEFAULT_COACH_ZONE = "Europe/Moscow"

@RestController
@RequestMapping("/admin")
class AdminController(
    private val adminService: AdminService,
    private val coachRequestService: CoachRequestService,
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

    @PostMapping("/coaches/{coachId}/telegram-link")
    fun telegramLink(
        @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) token: String?,
        @PathVariable coachId: UUID,
    ): TelegramStartResponse {
        authorize(token)
        return adminService.telegramClaimLink(coachId)
    }

    @GetMapping("/coach-requests")
    fun coachRequests(
        @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) token: String?,
    ): List<CoachRequestResponse> {
        authorize(token)
        return coachRequestService.pending()
    }

    @PostMapping("/coach-requests/{requestId}/approve")
    fun approveCoachRequest(
        @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) token: String?,
        @PathVariable requestId: UUID,
        @RequestParam(required = false) zoneId: String?,
    ): ApprovedCoachResponse {
        authorize(token)
        return coachRequestService.approve(
            requestId = requestId,
            zoneId = zoneId?.takeIf { it.isNotBlank() } ?: DEFAULT_COACH_ZONE,
        )
    }

    @PostMapping("/coach-requests/{requestId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun declineCoachRequest(
        @RequestHeader(name = ADMIN_TOKEN_HEADER, required = false) token: String?,
        @PathVariable requestId: UUID,
    ) {
        authorize(token)
        coachRequestService.decline(requestId)
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
