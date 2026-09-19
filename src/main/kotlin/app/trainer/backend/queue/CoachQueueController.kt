package app.trainer.backend.queue

import app.trainer.backend.checkin.CheckInService
import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.formcheck.FormCheckService
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

data class CoachQueuesResponse(
    val checkInsAwaiting: Int,
    val formChecksAwaiting: Int,
)

@RestController
class CoachQueueController(
    private val checkInService: CheckInService,
    private val formCheckService: FormCheckService,
) {

    @GetMapping("/coach/queues")
    fun queues(@CurrentUserId coachUserId: UUID): CoachQueuesResponse = CoachQueuesResponse(
        checkInsAwaiting = checkInService.awaitingCount(coachUserId),
        formChecksAwaiting = formCheckService.awaitingCount(coachUserId),
    )
}
