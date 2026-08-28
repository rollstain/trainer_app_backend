package app.trainer.backend.formcheck

import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.media.PrepareUploadRequest
import app.trainer.backend.media.PrepareUploadResponse
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class FormCheckController(private val formCheckService: FormCheckService) {

    @PostMapping("/form-checks/uploads")
    fun prepareUpload(
        @CurrentUserId clientUserId: UUID,
        @Valid @RequestBody request: PrepareUploadRequest,
    ): PrepareUploadResponse {
        return formCheckService.prepareUpload(clientUserId = clientUserId, request = request)
    }

    @PostMapping("/form-checks")
    fun create(
        @CurrentUserId clientUserId: UUID,
        @Valid @RequestBody request: CreateFormCheckRequest,
    ): FormCheckResponse {
        return formCheckService.create(clientUserId = clientUserId, request = request)
    }

    @GetMapping("/me/form-checks")
    fun ownFormChecks(@CurrentUserId clientUserId: UUID): List<FormCheckResponse> {
        return formCheckService.ownFormChecks(clientUserId = clientUserId)
    }

    @GetMapping("/coach/form-checks/awaiting")
    fun awaiting(@CurrentUserId coachUserId: UUID): List<FormCheckResponse> {
        return formCheckService.awaitingReview(coachUserId = coachUserId)
    }

    @PostMapping("/coach/form-checks/{formCheckId}/review")
    fun review(
        @CurrentUserId coachUserId: UUID,
        @PathVariable formCheckId: UUID,
        @Valid @RequestBody request: ReviewFormCheckRequest,
    ): FormCheckResponse {
        return formCheckService.review(
            coachUserId = coachUserId,
            formCheckId = formCheckId,
            request = request,
        )
    }
}
