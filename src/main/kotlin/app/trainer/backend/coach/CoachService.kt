package app.trainer.backend.coach

import app.trainer.backend.user.UserRepository
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class CoachService(
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val userRepository: UserRepository,
) {

    @Transactional(readOnly = true)
    fun clientsOfCoach(coachUserId: UUID): List<CoachClientResponse> {
        val coach = requireCoach(coachUserId)
        return coachClientRepository
            .findByCoachIdAndStatus(coachId = coach.id, status = CoachClientStatus.ACTIVE)
            .mapNotNull { link ->
                val user = userRepository.findByIdOrNull(link.userId) ?: return@mapNotNull null
                CoachClientResponse(
                    userId = user.id,
                    displayName = user.displayName,
                    status = link.status,
                )
            }
    }

    @Transactional(readOnly = true)
    fun policyOf(coachUserId: UUID): CoachPolicyResponse {
        val coach = requireCoach(coachUserId)
        return CoachPolicyResponse(cancellationWindowHours = coach.cancellationWindowHours)
    }

    @Transactional
    fun updatePolicy(coachUserId: UUID, request: UpdateCoachPolicyRequest): CoachPolicyResponse {
        val coach = requireCoach(coachUserId)
        coach.cancellationWindowHours = request.cancellationWindowHours
        return CoachPolicyResponse(cancellationWindowHours = coach.cancellationWindowHours)
    }

    private fun requireCoach(coachUserId: UUID): CoachEntity = coachRepository.findByUserId(coachUserId)
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")

    @Transactional(readOnly = true)
    fun coachesOfClient(userId: UUID): List<CoachSummaryResponse> {
        return coachClientRepository
            .findByUserId(userId)
            .filter { it.status == CoachClientStatus.ACTIVE }
            .mapNotNull { link ->
                val coach = coachRepository.findByIdOrNull(link.coachId) ?: return@mapNotNull null
                val coachUser = userRepository.findByIdOrNull(coach.userId) ?: return@mapNotNull null
                CoachSummaryResponse(
                    coachId = coach.id,
                    userId = coachUser.id,
                    displayName = coachUser.displayName,
                    zoneId = coach.zoneId,
                    cancellationWindowHours = coach.cancellationWindowHours,
                )
            }
    }
}
