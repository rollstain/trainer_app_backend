package app.trainer.backend.coachrequest

import app.trainer.backend.admin.AdminService
import app.trainer.backend.auth.external.ExternalIdentityRepository
import app.trainer.backend.auth.external.ExternalProvider
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val RETRY_AFTER_DAYS = 7L

@Service
class CoachRequestService(
    private val requestRepository: CoachRequestRepository,
    private val coachRepository: CoachRepository,
    private val userRepository: UserRepository,
    private val identityRepository: ExternalIdentityRepository,
    private val adminService: AdminService,
    private val clock: Clock,
) {

    @Transactional
    fun ask(userId: UUID, request: AskCoachAccessRequest): CoachAccessStatusResponse {
        val known = requestRepository.findByUserId(userId)
        if (known == null || known.status != CoachRequestStatus.PENDING) {
            saveAsked(userId = userId, request = request, known = known)
        }
        return statusOf(userId)
    }

    @Transactional(readOnly = true)
    fun statusOf(userId: UUID): CoachAccessStatusResponse {
        if (coachRepository.findByUserId(userId) != null) {
            return CoachAccessStatusResponse(
                status = CoachRequestStatus.APPROVED,
                about = null,
                askedAt = null,
                decidedAt = null,
                canAskAgainOn = null,
            )
        }
        val known = requestRepository.findByUserId(userId) ?: return CoachAccessStatusResponse(
            status = null,
            about = null,
            askedAt = null,
            decidedAt = null,
            canAskAgainOn = null,
        )
        return CoachAccessStatusResponse(
            status = known.status,
            about = known.about,
            askedAt = known.createdAt,
            decidedAt = known.decidedAt,
            canAskAgainOn = retryAllowedOn(known),
        )
    }

    @Transactional(readOnly = true)
    fun pending(): List<CoachRequestResponse> =
        requestRepository.findByStatusOrderByCreatedAtAsc(CoachRequestStatus.PENDING).mapNotNull(::toResponse)

    @Transactional(readOnly = true)
    fun decided(): List<CoachRequestResponse> =
        requestRepository.findByStatusNotOrderByDecidedAtDesc(CoachRequestStatus.PENDING).mapNotNull(::toResponse)

    @Transactional
    fun approve(requestId: UUID, zoneId: String): ApprovedCoachResponse {
        val request = requirePending(requestId)
        val coach = adminService.promoteToCoach(userId = request.userId, zoneId = zoneId)
        request.status = CoachRequestStatus.APPROVED
        request.decidedAt = Instant.now(clock)
        return ApprovedCoachResponse(coachId = coach.id, userId = request.userId)
    }

    @Transactional
    fun decline(requestId: UUID): UUID {
        val request = requirePending(requestId)
        request.status = CoachRequestStatus.DECLINED
        request.decidedAt = Instant.now(clock)
        return request.userId
    }

    private fun saveAsked(userId: UUID, request: AskCoachAccessRequest, known: CoachRequestEntity?) {
        val allowedOn = known?.let(::retryAllowedOn)
        if (allowedOn != null && allowedOn.isAfter(today())) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Новую заявку можно отправить $allowedOn")
        }
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден")
        user.displayName = request.displayName.trim()
        if (known != null) {
            known.about = request.about.trim()
            known.status = CoachRequestStatus.PENDING
            known.decidedAt = null
            return
        }
        requestRepository.save(
            CoachRequestEntity(
                id = UUID.randomUUID(),
                userId = userId,
                about = request.about.trim(),
                status = CoachRequestStatus.PENDING,
                createdAt = Instant.now(clock),
                decidedAt = null,
            )
        )
    }

    private fun retryAllowedOn(request: CoachRequestEntity): LocalDate? {
        if (request.status != CoachRequestStatus.DECLINED) return null
        val decidedAt = request.decidedAt ?: return null
        return decidedAt.plus(RETRY_AFTER_DAYS, ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toLocalDate()
    }

    private fun today(): LocalDate = Instant.now(clock).atZone(ZoneOffset.UTC).toLocalDate()

    private fun requirePending(requestId: UUID): CoachRequestEntity {
        val request = requestRepository.findByIdOrNull(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена")
        if (request.status != CoachRequestStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже рассмотрена")
        }
        return request
    }

    private fun toResponse(request: CoachRequestEntity): CoachRequestResponse? {
        val user = userRepository.findByIdOrNull(request.userId) ?: return null
        val telegram = identityRepository
            .findByUserId(request.userId)
            .firstOrNull { it.provider == ExternalProvider.TELEGRAM }
        return CoachRequestResponse(
            id = request.id,
            userId = request.userId,
            displayName = user.displayName,
            about = request.about,
            telegramUsername = telegram?.username,
            firstSeenAt = user.createdAt,
            createdAt = request.createdAt,
            status = request.status,
            decidedAt = request.decidedAt,
        )
    }
}
