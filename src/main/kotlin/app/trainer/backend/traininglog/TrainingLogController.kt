package app.trainer.backend.traininglog

import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.time.LocalDate
import java.util.UUID
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TrainingLogController(private val trainingLogService: TrainingLogService) {

    @GetMapping("/exercises")
    fun exercises(@CurrentUserId userId: UUID): List<ExerciseResponse> {
        return trainingLogService.availableExercises(userId = userId)
    }

    @PostMapping("/coach/exercises")
    fun createExercise(
        @CurrentUserId coachUserId: UUID,
        @Valid @RequestBody request: CreateExerciseRequest,
    ): ExerciseResponse {
        return trainingLogService.createExercise(coachUserId = coachUserId, request = request)
    }

    @GetMapping("/me/training-log")
    fun ownTrainingLog(
        @CurrentUserId userId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<TrainingLogEntryResponse> {
        return trainingLogService.ownEntries(clientUserId = userId, from = from, to = to)
    }

    @PutMapping("/me/training-log/{entryDate}")
    fun saveTrainingLog(
        @CurrentUserId userId: UUID,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) entryDate: LocalDate,
        @Valid @RequestBody request: SaveTrainingLogRequest,
    ): TrainingLogEntryResponse {
        return trainingLogService.saveEntry(
            clientUserId = userId,
            entryDate = entryDate,
            request = request,
        )
    }

    @GetMapping("/coach/clients/{clientUserId}/training-log")
    fun clientTrainingLog(
        @CurrentUserId coachUserId: UUID,
        @PathVariable clientUserId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<TrainingLogEntryResponse> {
        return trainingLogService.clientEntries(
            coachUserId = coachUserId,
            clientUserId = clientUserId,
            from = from,
            to = to,
        )
    }
}
