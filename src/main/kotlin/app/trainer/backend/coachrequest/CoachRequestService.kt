package app.trainer.backend.coachrequest

import app.trainer.backend.admin.AdminService
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class CoachRequestService(
    private val requestRepository: CoachRequestRepository,
    private val coachRepository: CoachRepository,
    private val userRepository: UserRepository,
    private val adminService: AdminService,
    private val clock: Clock,
) {

    @Transactional
    fun ask(userId: UUID): CoachRequestStatusResponse {
        if (coachRepository.findByUserId(userId) != null) {
            return CoachRequestStatusResponse(status = CoachRequestStatus.APPROVED)
        }
        val known = requestRepository.findByUserId(userId)
        if (known != null) {
            known.status = CoachRequestStatus.PENDING
            known.decidedAt = null
            return CoachRequestStatusResponse(status = known.status)
        }
        requestRepository.save(
            CoachRequestEntity(
                id = UUID.randomUUID(),
                userId = userId,
                status = CoachRequestStatus.PENDING,
                createdAt = Instant.now(clock),
                decidedAt = null,
            )
        )
        return CoachRequestStatusResponse(status = CoachRequestStatus.PENDING)
    }

    @Transactional(readOnly = true)
    fun pending(): List<CoachRequestResponse> = requestRepository
        .findByStatusOrderByCreatedAtAsc(CoachRequestStatus.PENDING)
        .mapNotNull { request ->
            val user = userRepository.findByIdOrNull(request.userId) ?: return@mapNotNull null
            CoachRequestResponse(
                id = request.id,
                userId = request.userId,
                displayName = user.displayName,
                createdAt = request.createdAt,
            )
        }

    @Transactional
    fun approve(requestId: UUID, zoneId: String): ApprovedCoachResponse {
        val request = requirePending(requestId)
        val coach = adminService.promoteToCoach(userId = request.userId, zoneId = zoneId)
        request.status = CoachRequestStatus.APPROVED
        request.decidedAt = Instant.now(clock)
        return ApprovedCoachResponse(coachId = coach.id, userId = request.userId)
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
}
