package app.trainer.backend.habit

import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.time.LocalDate
import java.util.UUID
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class HabitController(private val habitService: HabitService) {

    @GetMapping("/habits")
    fun ownHabits(
        @CurrentUserId clientUserId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<HabitResponse> {
        return habitService.ownHabits(clientUserId = clientUserId, from = from, to = to)
    }

    @PostMapping("/habits")
    fun createOwn(
        @CurrentUserId clientUserId: UUID,
        @Valid @RequestBody request: CreateHabitRequest,
    ): HabitResponse {
        return habitService.createOwn(clientUserId = clientUserId, request = request)
    }

    @PostMapping("/habits/{habitId}/marks/{date}")
    fun markDone(
        @CurrentUserId userId: UUID,
        @PathVariable habitId: UUID,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ) {
        habitService.mark(userId = userId, habitId = habitId, markDate = date, isDone = true)
    }

    @DeleteMapping("/habits/{habitId}/marks/{date}")
    fun markUndone(
        @CurrentUserId userId: UUID,
        @PathVariable habitId: UUID,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ) {
        habitService.mark(userId = userId, habitId = habitId, markDate = date, isDone = false)
    }

    @DeleteMapping("/habits/{habitId}")
    fun archive(@CurrentUserId userId: UUID, @PathVariable habitId: UUID) {
        habitService.archive(userId = userId, habitId = habitId)
    }

    @GetMapping("/coach/clients/{clientUserId}/habits")
    fun clientHabits(
        @CurrentUserId coachUserId: UUID,
        @PathVariable clientUserId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<HabitResponse> {
        return habitService.clientHabits(
            coachUserId = coachUserId,
            clientUserId = clientUserId,
            from = from,
            to = to,
        )
    }

    @PostMapping("/coach/clients/{clientUserId}/habits")
    fun createForClient(
        @CurrentUserId coachUserId: UUID,
        @PathVariable clientUserId: UUID,
        @Valid @RequestBody request: CreateHabitRequest,
    ): HabitResponse {
        return habitService.createForClient(
            coachUserId = coachUserId,
            clientUserId = clientUserId,
            request = request,
        )
    }
}
