package app.trainer.backend.auth.password

import jakarta.validation.constraints.NotBlank

data class PasswordSignUpRequest(
    @field:NotBlank
    val displayName: String,
    @field:NotBlank
    val email: String,
    val login: String?,
    @field:NotBlank
    val password: String,
    @field:NotBlank
    val deviceInfo: String,
)

data class PasswordSignInRequest(
    @field:NotBlank
    val identifier: String,
    @field:NotBlank
    val password: String,
    @field:NotBlank
    val deviceInfo: String,
)

data class PasswordResetRequest(
    @field:NotBlank
    val claimToken: String,
    @field:NotBlank
    val password: String,
    @field:NotBlank
    val deviceInfo: String,
)

data class SetPasswordRequest(
    val email: String?,
    val login: String?,
    val currentPassword: String?,
    @field:NotBlank
    val newPassword: String,
)

data class ForgotPasswordRequest(
    @field:NotBlank
    val email: String,
)

data class PasswordResetByEmailRequest(
    @field:NotBlank
    val token: String,
    @field:NotBlank
    val password: String,
    @field:NotBlank
    val deviceInfo: String,
)
