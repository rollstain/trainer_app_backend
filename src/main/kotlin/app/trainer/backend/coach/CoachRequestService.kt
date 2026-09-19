package app.trainer.backend.coach

import app.trainer.backend.auth.external.ExternalIdentityRepository
import app.trainer.backend.auth.external.ExternalProvider
import app.trainer.backend.auth.external.VerifiedIdentity
import app.trainer.backend.auth.external.subjectHashOf
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val DEFAULT_CANCELLATION_WINDOW_HOURS = 12
private const val DEFAULT_REMINDER_HOUR = 10
private const val RETRY_AFTER_DAYS = 7L

@Service
class CoachRequestService(
    private val requestRepository: CoachRequestRepository,
    private val userRepository: UserRepository,
    private val coachRepository: CoachRepository,
    private val identityRepository: ExternalIdentityRepository,
    private val clock: Clock,
) {

    @Transactional
    fun ask(userId: UUID, displayName: String, zoneId: String) {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден")
        if (coachRepository.findByUserId(userId) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Вы уже тренер")
        }
        val name = displayName.trim()
        if (name.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите имя")
        }
        val zone = knownZoneOrThrow(zoneId)
        val known = requestRepository.findByUserId(userId)
        requireMayAsk(known)
        user.displayName = name
        if (known == null) {
            requestRepository.save(pendingRequest(userId = userId, zoneId = zone))
            return
        }
        known.zoneId = zone
        if (known.status != CoachRequestStatus.PENDING) reopen(known)
    }

    @Transactional(readOnly = true)
    fun statusOf(userId: UUID): CoachRequestStatusResponse? {
        if (coachRepository.findByUserId(userId) != null) return null
        val known = requestRepository.findByUserId(userId) ?: return null
        return CoachRequestStatusResponse(
            status = known.status,
            askedAt = known.createdAt,
            decidedAt = known.decidedAt,
            canAskAgainOn = retryAllowedOn(known),
        )
    }

    @Transactional(readOnly = true)
    fun unannounced(): List<TelegramCoachRequestResponse> =
        requestRepository
            .findByStatusAndAnnouncedAtIsNullOrderByCreatedAtAsc(CoachRequestStatus.PENDING)
            .mapNotNull { request ->
                val user = userRepository.findByIdOrNull(request.userId) ?: return@mapNotNull null
                TelegramCoachRequestResponse(
                    id = request.id,
                    displayName = user.displayName,
                    email = user.email,
                    login = user.login,
                    telegramUsername = identityRepository
                        .findByUserIdAndProvider(userId = user.id, provider = ExternalProvider.TELEGRAM)
                        ?.username,
                    registeredAt = user.createdAt,
                    askedAt = request.createdAt,
                )
            }

    @Transactional
    fun markAnnounced(requestId: UUID) {
        val request = requestOrThrow(requestId)
        if (request.announcedAt == null) request.announcedAt = Instant.now(clock)
    }

    @Transactional
    fun decide(requestId: UUID, approve: Boolean, telegramUserId: String): CoachDecisionResponse {
        requireOwnerTelegram(telegramUserId)
        val request = requestOrThrow(requestId)
        if (request.status != CoachRequestStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже рассмотрена")
        }
        val user = userRepository.findByIdOrNull(request.userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден")
        request.status = if (approve) CoachRequestStatus.APPROVED else CoachRequestStatus.DECLINED
        request.decidedAt = Instant.now(clock)
        if (approve) promote(user = user, zoneId = request.zoneId)
        return CoachDecisionResponse(status = request.status, displayName = user.displayName)
    }

    private fun requireMayAsk(known: CoachRequestEntity?) {
        val allowedOn = known?.let(::retryAllowedOn) ?: return
        if (allowedOn.isAfter(today())) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Новую заявку можно отправить $allowedOn")
        }
    }

    private fun reopen(request: CoachRequestEntity) {
        request.status = CoachRequestStatus.PENDING
        request.createdAt = Instant.now(clock)
        request.announcedAt = null
        request.decidedAt = null
    }

    private fun pendingRequest(userId: UUID, zoneId: String) = CoachRequestEntity(
        id = UUID.randomUUID(),
        userId = userId,
        zoneId = zoneId,
        status = CoachRequestStatus.PENDING,
        createdAt = Instant.now(clock),
        announcedAt = null,
        decidedAt = null,
    )

    private fun promote(user: UserEntity, zoneId: String) {
        if (coachRepository.findByUserId(user.id) != null) return
        coachRepository.save(
            CoachEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                zoneId = zoneId,
                cancellationWindowHours = DEFAULT_CANCELLATION_WINDOW_HOURS,
                reminderHour = DEFAULT_REMINDER_HOUR,
                sessionRemindersEnabled = true,
                diaryRemindersEnabled = true,
                checkInRemindersEnabled = true,
                createdAt = Instant.now(clock),
            )
        )
    }

    private fun requireOwnerTelegram(telegramUserId: String) {
        val subjectHash = subjectHashOf(
            VerifiedIdentity(
                provider = ExternalProvider.TELEGRAM,
                subject = telegramUserId,
                displayName = null,
                username = null,
            )
        )
        val identity = identityRepository.findByProviderAndSubjectHash(
            provider = ExternalProvider.TELEGRAM,
            subjectHash = subjectHash,
        )
        val decider = identity?.let { userRepository.findByIdOrNull(it.userId) }
        if (decider?.isOwner != true) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Заявки решает только владелец")
        }
    }

    private fun requestOrThrow(requestId: UUID): CoachRequestEntity =
        requestRepository.findByIdOrNull(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена")

    private fun retryAllowedOn(request: CoachRequestEntity): LocalDate? {
        if (request.status != CoachRequestStatus.DECLINED) return null
        val decidedAt = request.decidedAt ?: return null
        return decidedAt.plus(RETRY_AFTER_DAYS, ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toLocalDate()
    }

    private fun today(): LocalDate = Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate()

    private fun knownZoneOrThrow(zoneId: String): String {
        val zone = zoneId.trim()
        if (zone !in ZoneId.getAvailableZoneIds()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный часовой пояс")
        }
        return zone
    }
}
