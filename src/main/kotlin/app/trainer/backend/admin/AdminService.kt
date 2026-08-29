package app.trainer.backend.admin

import app.trainer.backend.auth.AuthProperties
import app.trainer.backend.auth.InviteCodeGenerator
import app.trainer.backend.auth.InviteEntity
import app.trainer.backend.auth.InviteRepository
import app.trainer.backend.auth.external.TelegramLoginService
import app.trainer.backend.auth.external.TelegramStartResponse
import app.trainer.backend.auth.password.normalizedEmailOrNull
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val DEFAULT_CANCELLATION_WINDOW_HOURS = 12
private const val DEFAULT_REMINDER_HOUR = 10

@Service
class AdminService(
    private val userRepository: UserRepository,
    private val coachRepository: CoachRepository,
    private val inviteRepository: InviteRepository,
    private val inviteCodeGenerator: InviteCodeGenerator,
    private val telegramLoginService: TelegramLoginService,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    @Transactional
    fun onboardCoach(request: CreateCoachRequest): CoachOnboardedResponse {
        val now = Instant.now(clock)
        val phone = request.phone?.trim()?.ifEmpty { null }
        val email = request.email?.trim()?.ifEmpty { null }?.let {
            normalizedEmailOrNull(it)
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте адрес почты")
        }
        requireContactIsFree(phone = phone, email = email)
        val user = userRepository.save(
            UserEntity(
                id = UUID.randomUUID(),
                displayName = request.displayName.trim(),
                phone = phone,
                email = email,
                login = null,
                isOwner = false,
                createdAt = now,
            )
        )
        val coach = coachRepository.save(
            CoachEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                zoneId = request.zoneId.trim(),
                cancellationWindowHours = DEFAULT_CANCELLATION_WINDOW_HOURS,
                reminderHour = DEFAULT_REMINDER_HOUR,
                sessionRemindersEnabled = true,
                diaryRemindersEnabled = true,
                checkInRemindersEnabled = true,
                createdAt = now,
            )
        )
        val invite = mintLoginCode(coach = coach, now = now)
        return CoachOnboardedResponse(
            coachId = coach.id,
            userId = user.id,
            code = invite.code,
            expiresAt = invite.expiresAt,
        )
    }

    @Transactional
    fun issueLoginCode(coachId: UUID): LoginCodeResponse {
        val coach = coachRepository.findByIdOrNull(coachId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Тренер не найден")
        val invite = mintLoginCode(coach = coach, now = Instant.now(clock))
        return LoginCodeResponse(
            coachId = coach.id,
            code = invite.code,
            expiresAt = invite.expiresAt,
        )
    }

    @Transactional
    fun telegramClaimLink(coachId: UUID): TelegramStartResponse {
        val coach = coachRepository.findByIdOrNull(coachId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Тренер не найден")
        return telegramLoginService.startClaim(targetUserId = coach.userId)
    }

    private fun mintLoginCode(coach: CoachEntity, now: Instant): InviteEntity {
        val invite = InviteEntity(
            id = UUID.randomUUID(),
            coachId = coach.id,
            targetUserId = coach.userId,
            code = inviteCodeGenerator.nextUnusedCode(),
            expiresAt = now.plus(properties.inviteTtlHours, ChronoUnit.HOURS),
            usedAt = null,
            usedByUserId = null,
            createdAt = now,
        )
        return inviteRepository.save(invite)
    }

    private fun requireContactIsFree(phone: String?, email: String?) {
        if (phone != null && userRepository.findByPhone(phone) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Этот телефон уже занят")
        }
        if (email != null && userRepository.findByEmail(email) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Эта почта уже занята")
        }
    }
}
