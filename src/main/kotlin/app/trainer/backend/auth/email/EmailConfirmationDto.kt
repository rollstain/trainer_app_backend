package app.trainer.backend.auth.email

import jakarta.validation.constraints.NotBlank

data class ConfirmEmailRequest(
    @field:NotBlank
    val token: String,
)
