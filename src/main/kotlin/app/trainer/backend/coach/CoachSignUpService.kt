package app.trainer.backend.coach

import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val DEFAULT_CANCELLATION_WINDOW_HOURS = 12
private const val DEFAULT_REMINDER_HOUR = 10

@Service
class CoachSignUpService(
    private val userRepository: UserRepository,
    private val coachRepository: CoachRepository,
    private val clock: Clock,
) {

    @Transactional
    fun signUp(userId: UUID, displayName: String, zoneId: String): CoachEntity {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден")
        if (coachRepository.findByUserId(user.id) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Вы уже тренер")
        }
        val name = displayName.trim()
        if (name.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите имя")
        }
        user.displayName = name
        return coachRepository.save(
            CoachEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                zoneId = knownZoneOrThrow(zoneId),
                cancellationWindowHours = DEFAULT_CANCELLATION_WINDOW_HOURS,
                reminderHour = DEFAULT_REMINDER_HOUR,
                sessionRemindersEnabled = true,
                diaryRemindersEnabled = true,
                checkInRemindersEnabled = true,
                createdAt = Instant.now(clock),
            )
        )
    }

    private fun knownZoneOrThrow(zoneId: String): String {
        val zone = zoneId.trim()
        if (zone !in ZoneId.getAvailableZoneIds()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный часовой пояс")
        }
        return zone
    }
}
