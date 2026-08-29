package app.trainer.backend.coachrequest

import app.trainer.backend.admin.AdminService
import app.trainer.backend.admin.CreateCoachRequest
import app.trainer.backend.auth.external.ExternalAuthService
import app.trainer.backend.auth.external.ExternalProvider
import app.trainer.backend.auth.external.VerifiedIdentity
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val NAMELESS_COACH = "Тренер"

@Service
class CoachRequestService(
    private val requestRepository: CoachRequestRepository,
    private val adminService: AdminService,
    private val externalAuthService: ExternalAuthService,
    private val clock: Clock,
) {

    @Transactional
    fun ask(telegramUserId: String, telegramDisplayName: String?): CoachRequestStatus {
        val known = requestRepository.findByTelegramUserId(telegramUserId)
        if (known != null) {
            if (known.status == CoachRequestStatus.APPROVED) return known.status
            known.telegramDisplayName = telegramDisplayName
            known.status = CoachRequestStatus.PENDING
            known.decidedAt = null
            return known.status
        }
        requestRepository.save(
            CoachRequestEntity(
                id = UUID.randomUUID(),
                telegramUserId = telegramUserId,
                telegramDisplayName = telegramDisplayName,
                status = CoachRequestStatus.PENDING,
                createdAt = Instant.now(clock),
                decidedAt = null,
            )
        )
        return CoachRequestStatus.PENDING
    }

    @Transactional(readOnly = true)
    fun pending(): List<CoachRequestResponse> = requestRepository
        .findByStatusOrderByCreatedAtAsc(CoachRequestStatus.PENDING)
        .map(::toResponse)

    @Transactional
    fun approve(requestId: UUID, zoneId: String): ApprovedCoachResponse {
        val request = requirePending(requestId)
        val onboarded = adminService.onboardCoach(
            CreateCoachRequest(
                displayName = request.telegramDisplayName?.takeIf { it.isNotBlank() } ?: NAMELESS_COACH,
                zoneId = zoneId,
                phone = null,
                email = null,
            )
        )
        externalAuthService.linkVerified(
            userId = onboarded.userId,
            verified = VerifiedIdentity(
                provider = ExternalProvider.TELEGRAM,
                subject = request.telegramUserId,
                displayName = request.telegramDisplayName,
            ),
        )
        request.status = CoachRequestStatus.APPROVED
        request.decidedAt = Instant.now(clock)
        return ApprovedCoachResponse(
            coachId = onboarded.coachId,
            userId = onboarded.userId,
            telegramUserId = request.telegramUserId,
        )
    }

    @Transactional
    fun decline(requestId: UUID) {
        val request = requirePending(requestId)
        request.status = CoachRequestStatus.DECLINED
        request.decidedAt = Instant.now(clock)
    }

    private fun requirePending(requestId: UUID): CoachRequestEntity {
        val request = requestRepository.findByIdOrNull(requestId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена")
        if (request.status != CoachRequestStatus.PENDING) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже рассмотрена")
        }
        return request
    }

    private fun toResponse(request: CoachRequestEntity): CoachRequestResponse = CoachRequestResponse(
        id = request.id,
        telegramUserId = request.telegramUserId,
        telegramDisplayName = request.telegramDisplayName,
        createdAt = request.createdAt,
    )
}
