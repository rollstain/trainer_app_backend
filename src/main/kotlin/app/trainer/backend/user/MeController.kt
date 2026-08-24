package app.trainer.backend.user

import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.config.CurrentUserId
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import jakarta.validation.Valid
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class MeResponse(
    val userId: UUID,
    val displayName: String,
    val phone: String?,
    val email: String?,
    val coachId: UUID?,
    val zoneId: String?,
)

data class UpdateContactRequest(
    val phone: String?,
    val email: String?,
)

@RestController
class MeController(
    private val userRepository: UserRepository,
    private val coachRepository: CoachRepository,
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
        val email = request.email?.trim()?.ifEmpty { null }
        if (phone == null && email == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите телефон или почту")
        }
        requireContactIsFree(userId = userId, phone = phone, email = email)
        if (phone != null) user.phone = phone
        if (email != null) user.email = email
        return toResponse(user)
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

    @GetMapping("/me")
    fun me(@CurrentUserId userId: UUID): MeResponse {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден")
        return toResponse(user)
    }

    private fun toResponse(user: UserEntity): MeResponse {
        val coach = coachRepository.findByUserId(user.id)
        return MeResponse(
            userId = user.id,
            displayName = user.displayName,
            phone = user.phone,
            email = user.email,
            coachId = coach?.id,
            zoneId = coach?.zoneId,
        )
    }
}
