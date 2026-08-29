package app.trainer.backend.coachrequest

import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.config.CurrentUserId
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val NEW_COACH_ZONE = "Europe/Moscow"

@RestController
@RequestMapping("/owner")
class OwnerController(
    private val coachRequestService: CoachRequestService,
    private val coachRepository: CoachRepository,
) {

    @GetMapping("/coach-requests")
    fun pending(@CurrentUserId userId: UUID): List<CoachRequestResponse> {
        requireOwner(userId)
        return coachRequestService.pending()
    }

    @PostMapping("/coach-requests/{requestId}/approve")
    fun approve(@CurrentUserId userId: UUID, @PathVariable requestId: UUID): ApprovedCoachResponse {
        requireOwner(userId)
        return coachRequestService.approve(requestId = requestId, zoneId = NEW_COACH_ZONE)
    }

    @PostMapping("/coach-requests/{requestId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun decline(@CurrentUserId userId: UUID, @PathVariable requestId: UUID) {
        requireOwner(userId)
        coachRequestService.decline(requestId)
    }

    private fun requireOwner(userId: UUID) {
        val coach = coachRepository.findByUserId(userId)
        if (coach == null || !coach.isOwner) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Заявки рассматривает владелец")
        }
    }
}
