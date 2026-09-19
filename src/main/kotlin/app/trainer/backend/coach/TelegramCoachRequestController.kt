package app.trainer.backend.coach

import app.trainer.backend.auth.external.TELEGRAM_BOT_SECRET_HEADER
import app.trainer.backend.auth.external.TelegramBotGuard
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/telegram/coach-requests")
class TelegramCoachRequestController(
    private val botGuard: TelegramBotGuard,
    private val coachRequestService: CoachRequestService,
) {

    @GetMapping("/unannounced")
    fun unannounced(
        @RequestHeader(name = TELEGRAM_BOT_SECRET_HEADER, required = false) secret: String?,
    ): List<TelegramCoachRequestResponse> {
        botGuard.authorize(secret)
        return coachRequestService.unannounced()
    }

    @PostMapping("/{requestId}/announced")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun announced(
        @RequestHeader(name = TELEGRAM_BOT_SECRET_HEADER, required = false) secret: String?,
        @PathVariable requestId: UUID,
    ) {
        botGuard.authorize(secret)
        coachRequestService.markAnnounced(requestId)
    }

    @PostMapping("/{requestId}/decision")
    fun decide(
        @RequestHeader(name = TELEGRAM_BOT_SECRET_HEADER, required = false) secret: String?,
        @PathVariable requestId: UUID,
        @Valid @RequestBody request: CoachDecisionRequest,
    ): CoachDecisionResponse {
        botGuard.authorize(secret)
        return coachRequestService.decide(
            requestId = requestId,
            approve = request.approve,
            telegramUserId = request.telegramUserId,
        )
    }
}
