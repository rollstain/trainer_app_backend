package app.trainer.backend.user

import app.trainer.backend.auth.password.PasswordStore
import app.trainer.backend.auth.password.normalizedEmailOrNull
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.coach.CoachSignUpService
import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.time.Instant
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class MeResponse(
    val userId: UUID,
    val displayName: String,
    val phone: String?,
    val email: String?,
    val login: String?,
    val hasPassword: Boolean,
    val passwordUpdatedAt: Instant?,
    val coachId: UUID?,
    val zoneId: String?,
    val hasCoach: Boolean,
    val isOwner: Boolean,
)

data class BecomeCoachRequest(
    val displayName: String,
    val zoneId: String,
)

data class UpdateContactRequest(
    val phone: String?,
    val email: String?,
    val currentPassword: String?,
)

@RestController
class MeController(
    private val userRepository: UserRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val passwordStore: PasswordStore,
    private val coachSignUpService: CoachSignUpService,
) {

    @PatchMapping("/me/contact")
    @Transactional
    fun updateContact(
        @CurrentUserId userId: UUID,
        @Valid @RequestBody request: UpdateContactRequest,
    ): MeResponse {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден")
        val phone = request.phone?.trim()?.ifEmpty { null }
        val email = request.email?.trim()?.ifEmpty { null }?.let {
            normalizedEmailOrNull(it)
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте адрес почты")
        }
        if (phone == null && email == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите телефон или почту")
        }
        if (email != null && email != user.email) {
            requireCurrentPassword(userId = userId, provided = request.currentPassword)
        }
        requireContactIsFree(userId = userId, phone = phone, email = email)
        if (phone != null) user.phone = phone
        if (email != null) user.email = email
        return toResponse(user)
    }

    private fun requireCurrentPassword(userId: UUID, provided: String?) {
        val credential = passwordStore.credentialOf(userId) ?: return
        val matches = provided != null && passwordStore.matches(credential = credential, password = provided)
        if (!matches) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Текущий пароль неверен")
        }
    }

    private fun requireContactIsFree(userId: UUID, phone: String?, email: String?) {
        val takenByPhone = phone?.let(userRepository::findByPhone)
        if (takenByPhone != null && takenByPhone.id != userId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Этот телефон уже занят")
        }
        val takenByEmail = email?.let(userRepository::findByEmail)
        if (takenByEmail != null && takenByEmail.id != userId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Эта почта уже занята")
        }
    }

    @PostMapping("/me/coach")
    fun becomeCoach(
        @CurrentUserId userId: UUID,
        @Valid @RequestBody request: BecomeCoachRequest,
    ): MeResponse {
        coachSignUpService.signUp(
            userId = userId,
            displayName = request.displayName,
            zoneId = request.zoneId,
        )
        return me(userId)
    }

    @GetMapping("/me")
    fun me(@CurrentUserId userId: UUID): MeResponse {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден")
        return toResponse(user)
    }

    private fun toResponse(user: UserEntity): MeResponse {
        val coach = coachRepository.findByUserId(user.id)
        val credential = passwordStore.credentialOf(user.id)
        return MeResponse(
            userId = user.id,
            displayName = user.displayName,
            phone = user.phone,
            email = user.email,
            login = user.login,
            hasPassword = credential != null,
            passwordUpdatedAt = credential?.updatedAt,
            coachId = coach?.id,
            zoneId = coach?.zoneId,
            hasCoach = coachClientRepository
                .findByUserId(user.id)
                .any { it.status == CoachClientStatus.ACTIVE },
            isOwner = user.isOwner,
        )
    }
}
