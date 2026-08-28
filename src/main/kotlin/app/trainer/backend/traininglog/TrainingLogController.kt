package app.trainer.backend.traininglog

import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.config.pageResponse
import app.trainer.backend.media.PrepareUploadRequest
import app.trainer.backend.media.PrepareUploadResponse
import jakarta.validation.Valid
import java.time.LocalDate
import java.util.UUID
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class TrainingLogController(private val trainingLogService: TrainingLogService) {

    @GetMapping("/exercises")
    fun exercises(
        @CurrentUserId userId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) after: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) muscle: List<MuscleGroup>?,
        @RequestParam(required = false) equipment: List<Equipment>?,
        @RequestParam(required = false) owner: ExerciseOwnerKind?,
    ): ResponseEntity<List<ExerciseResponse>> {
        return pageResponse(
            trainingLogService.availableExercises(
                userId = userId,
                limit = limit,
                after = after,
                filter = ExerciseFilter(
                    search = search,
                    muscles = muscle.orEmpty(),
                    equipment = equipment.orEmpty(),
                    ownerKind = owner,
                ),
            )
        )
    }

    @GetMapping("/coach/clients/diary-summary")
    fun diarySummary(
        @CurrentUserId coachUserId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<ClientDiarySummaryResponse> {
        return trainingLogService.diarySummary(coachUserId = coachUserId, from = from, to = to)
    }

    @PostMapping("/exercises")
    fun createExercise(
        @CurrentUserId userId: UUID,
        @Valid @RequestBody request: CreateExerciseRequest,
    ): ExerciseResponse {
        return trainingLogService.createExercise(userId = userId, request = request)
    }

    @PostMapping("/coach/exercises")
    fun createCoachExercise(
        @CurrentUserId userId: UUID,
        @Valid @RequestBody request: CreateExerciseRequest,
    ): ExerciseResponse {
        return trainingLogService.createExercise(userId = userId, request = request)
    }

    @DeleteMapping("/exercises/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archiveExercise(@CurrentUserId userId: UUID, @PathVariable exerciseId: UUID) {
        trainingLogService.archiveExercise(userId = userId, exerciseId = exerciseId)
    }

    @PostMapping("/coach/exercises/video-uploads")
    fun prepareVideoUpload(
        @CurrentUserId coachUserId: UUID,
        @Valid @RequestBody request: PrepareUploadRequest,
    ): PrepareUploadResponse {
        return trainingLogService.prepareVideoUpload(coachUserId = coachUserId, request = request)
    }

    @PutMapping("/coach/exercises/{exerciseId}/video")
    fun attachVideo(
        @CurrentUserId coachUserId: UUID,
        @PathVariable exerciseId: UUID,
        @Valid @RequestBody request: AttachExerciseVideoRequest,
    ): ExerciseResponse {
        return trainingLogService.attachVideo(
            coachUserId = coachUserId,
            exerciseId = exerciseId,
            mediaFileId = request.mediaFileId,
        )
    }

    @DeleteMapping("/coach/exercises/{exerciseId}/video")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun detachVideo(@CurrentUserId coachUserId: UUID, @PathVariable exerciseId: UUID) {
        trainingLogService.detachVideo(coachUserId = coachUserId, exerciseId = exerciseId)
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
