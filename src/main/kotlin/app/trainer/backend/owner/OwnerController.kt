package app.trainer.backend.owner

import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.config.pageResponse
import java.util.UUID
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/owner")
class OwnerController(private val ownerCoachService: OwnerCoachService) {

    @GetMapping("/coaches")
    fun coaches(
        @CurrentUserId userId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) after: String?,
    ): ResponseEntity<List<OwnerCoachResponse>> {
        return pageResponse(ownerCoachService.coaches(ownerUserId = userId, limit = limit, after = after))
    }

    @GetMapping("/coaches/{coachId}")
    fun coach(@CurrentUserId userId: UUID, @PathVariable coachId: UUID): OwnerCoachCardResponse {
        return ownerCoachService.card(ownerUserId = userId, coachId = coachId)
    }
}
