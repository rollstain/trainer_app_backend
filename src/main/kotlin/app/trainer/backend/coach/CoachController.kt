package app.trainer.backend.coach

import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.config.pageResponse
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class CoachController(private val coachService: CoachService) {

    @GetMapping("/coach/clients")
    fun clients(
        @CurrentUserId coachUserId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) after: String?,
    ): ResponseEntity<List<CoachClientResponse>> {
        return pageResponse(
            coachService.clientsOfCoach(coachUserId = coachUserId, limit = limit, after = after)
        )
    }

    @DeleteMapping("/coach/clients/{clientUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archiveClient(@CurrentUserId coachUserId: UUID, @PathVariable clientUserId: UUID) {
        coachService.archiveClient(coachUserId = coachUserId, clientUserId = clientUserId)
    }

    @GetMapping("/coach/policy")
    fun policy(@CurrentUserId coachUserId: UUID): CoachPolicyResponse {
        return coachService.policyOf(coachUserId = coachUserId)
    }

    @PatchMapping("/coach/policy")
    fun updatePolicy(
        @CurrentUserId coachUserId: UUID,
        @Valid @RequestBody request: UpdateCoachPolicyRequest,
    ): CoachPolicyResponse {
        return coachService.updatePolicy(coachUserId = coachUserId, request = request)
    }

    @GetMapping("/me/coaches")
    fun coaches(@CurrentUserId userId: UUID): List<CoachSummaryResponse> {
        return coachService.coachesOfClient(userId = userId)
    }
}
