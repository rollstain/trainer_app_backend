package app.trainer.backend.coach

import app.trainer.backend.config.CurrentUserId
import java.util.UUID
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class CoachController(private val coachService: CoachService) {

    @GetMapping("/coach/clients")
    fun clients(@CurrentUserId coachUserId: UUID): List<CoachClientResponse> {
        return coachService.clientsOfCoach(coachUserId = coachUserId)
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
