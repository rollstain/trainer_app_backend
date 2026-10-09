package app.trainer.backend.habit

import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class HabitSetController(private val habitSetService: HabitSetService) {

    @GetMapping("/coach/habit-sets")
    fun sets(@CurrentUserId coachUserId: UUID): List<HabitSetResponse> {
        return habitSetService.setsOfCoach(coachUserId = coachUserId)
    }

    @PostMapping("/coach/habit-sets")
    fun create(
        @CurrentUserId coachUserId: UUID,
        @Valid @RequestBody request: HabitSetRequest,
    ): HabitSetResponse {
        return habitSetService.create(coachUserId = coachUserId, request = request)
    }

    @PutMapping("/coach/habit-sets/{setId}")
    fun update(
        @CurrentUserId coachUserId: UUID,
        @PathVariable setId: UUID,
        @Valid @RequestBody request: HabitSetRequest,
    ): HabitSetResponse {
        return habitSetService.update(coachUserId = coachUserId, setId = setId, request = request)
    }

    @DeleteMapping("/coach/habit-sets/{setId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@CurrentUserId coachUserId: UUID, @PathVariable setId: UUID) {
        habitSetService.delete(coachUserId = coachUserId, setId = setId)
    }

    @PostMapping("/coach/clients/{clientUserId}/habit-sets/{setId}")
    fun assign(
        @CurrentUserId coachUserId: UUID,
        @PathVariable clientUserId: UUID,
        @PathVariable setId: UUID,
        @Valid @RequestBody(required = false) request: AssignHabitSetRequest?,
    ): List<HabitResponse> {
        return habitSetService.assign(
            coachUserId = coachUserId,
            clientUserId = clientUserId,
            setId = setId,
            request = request,
        )
    }
}
