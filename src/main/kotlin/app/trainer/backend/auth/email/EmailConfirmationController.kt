package app.trainer.backend.auth.email

import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class EmailConfirmationController(private val emailConfirmationService: EmailConfirmationService) {

    @PostMapping("/auth/email/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun confirm(@Valid @RequestBody request: ConfirmEmailRequest) {
        emailConfirmationService.confirm(request)
    }

    @PostMapping("/me/email/confirm-request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resend(@CurrentUserId userId: UUID) {
        emailConfirmationService.resend(userId)
    }
}
