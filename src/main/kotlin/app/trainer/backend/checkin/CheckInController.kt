package app.trainer.backend.checkin

import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.media.PrepareUploadRequest
import app.trainer.backend.media.PrepareUploadResponse
import jakarta.validation.Valid
import java.time.LocalDate
import java.util.UUID
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class CheckInController(private val checkInService: CheckInService) {

    @PutMapping("/check-ins/{date}")
    fun save(
        @CurrentUserId clientUserId: UUID,
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @Valid @RequestBody request: SaveCheckInRequest,
    ): CheckInResponse {
        return checkInService.save(clientUserId = clientUserId, checkInDate = date, request = request)
    }

    @PostMapping("/check-ins/photos")
    fun preparePhotoUpload(
        @CurrentUserId clientUserId: UUID,
        @Valid @RequestBody request: PrepareUploadRequest,
    ): PrepareUploadResponse {
        return checkInService.preparePhotoUpload(clientUserId = clientUserId, request = request)
    }

    @DeleteMapping("/check-ins/photos/{photoId}")
    fun deletePhoto(@CurrentUserId clientUserId: UUID, @PathVariable photoId: UUID) {
        checkInService.deletePhoto(clientUserId = clientUserId, photoId = photoId)
    }

    @GetMapping("/check-ins")
    fun ownCheckIns(
        @CurrentUserId clientUserId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<CheckInResponse> {
        return checkInService.ownCheckIns(clientUserId = clientUserId, from = from, to = to)
    }

    @GetMapping("/coach/check-ins/awaiting")
    fun awaiting(@CurrentUserId coachUserId: UUID): List<AwaitingCheckInResponse> {
        return checkInService.awaitingReview(coachUserId = coachUserId)
    }

    @PostMapping("/coach/clients/{clientUserId}/check-ins/{checkInId}/review")
    fun review(
        @CurrentUserId coachUserId: UUID,
        @PathVariable clientUserId: UUID,
        @PathVariable checkInId: UUID,
        @Valid @RequestBody request: ReviewCheckInRequest,
    ): CheckInResponse {
        return checkInService.review(
            coachUserId = coachUserId,
            clientUserId = clientUserId,
            checkInId = checkInId,
            request = request,
        )
    }

    @GetMapping("/coach/clients/{clientUserId}/check-ins")
    fun clientCheckIns(
        @CurrentUserId coachUserId: UUID,
        @PathVariable clientUserId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<CheckInResponse> {
        return checkInService.clientCheckIns(
            coachUserId = coachUserId,
            clientUserId = clientUserId,
            from = from,
            to = to,
        )
    }
}
