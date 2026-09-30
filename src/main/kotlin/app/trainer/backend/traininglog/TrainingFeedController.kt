package app.trainer.backend.traininglog

import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.config.pageResponse
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class TrainingFeedController(private val trainingFeedService: TrainingFeedService) {

    @GetMapping("/coach/training-feed")
    fun feed(
        @CurrentUserId coachUserId: UUID,
        @RequestParam(defaultValue = "false") unseenOnly: Boolean,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) after: String?,
    ): ResponseEntity<List<TrainingFeedItemResponse>> = pageResponse(
        trainingFeedService.feed(coachUserId = coachUserId, unseenOnly = unseenOnly, limit = limit, after = after)
    )

    @PostMapping("/coach/training-feed/{entryId}/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markSeen(@CurrentUserId coachUserId: UUID, @PathVariable entryId: UUID) {
        trainingFeedService.markSeen(coachUserId = coachUserId, entryId = entryId)
    }
}
