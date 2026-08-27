package app.trainer.backend.coach

import app.trainer.backend.clientnotes.ClientNoteKind
import app.trainer.backend.clientnotes.ClientNoteRepository
import app.trainer.backend.schedule.ScheduleService
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
    private val clientNoteRepository: ClientNoteRepository,
    private val scheduleService: ScheduleService,
) {

    @Transactional(readOnly = true)
    fun clientsOfCoach(coachUserId: UUID): List<CoachClientResponse> {
        val coach = requireCoach(coachUserId)
        val withMedicalNotes = clientNoteRepository
            .findClientUserIdsWithKind(coachId = coach.id, kind = ClientNoteKind.MEDICAL)
            .toSet()
        return coachClientRepository
            .findByCoachIdAndStatus(coachId = coach.id, status = CoachClientStatus.ACTIVE)
            .mapNotNull { link ->
                val user = userRepository.findByIdOrNull(link.userId) ?: return@mapNotNull null
                CoachClientResponse(
                    userId = user.id,
                    displayName = user.displayName,
                    status = link.status,
                    hasMedicalNotes = user.id in withMedicalNotes,
                    linkedAt = link.createdAt,
                )
            }
    }

    @Transactional(readOnly = true)
    fun policyOf(coachUserId: UUID): CoachPolicyResponse {
        return toPolicyResponse(requireCoach(coachUserId))
    }

    @Transactional
    fun updatePolicy(coachUserId: UUID, request: UpdateCoachPolicyRequest): CoachPolicyResponse {
        val coach = requireCoach(coachUserId)
        request.cancellationWindowHours?.let { coach.cancellationWindowHours = it }
        request.reminderHour?.let { coach.reminderHour = it }
        request.sessionRemindersEnabled?.let { coach.sessionRemindersEnabled = it }
        request.diaryRemindersEnabled?.let { coach.diaryRemindersEnabled = it }
        request.checkInRemindersEnabled?.let { coach.checkInRemindersEnabled = it }
        return toPolicyResponse(coach)
    }

    private fun toPolicyResponse(coach: CoachEntity): CoachPolicyResponse = CoachPolicyResponse(
        cancellationWindowHours = coach.cancellationWindowHours,
        reminderHour = coach.reminderHour,
        sessionRemindersEnabled = coach.sessionRemindersEnabled,
        diaryRemindersEnabled = coach.diaryRemindersEnabled,
        checkInRemindersEnabled = coach.checkInRemindersEnabled,
    )

    @Transactional
    fun archiveClient(coachUserId: UUID, clientUserId: UUID) {
        val coach = requireCoach(coachUserId)
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coach.id, userId = clientUserId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Подопечный не найден")
        }
        link.status = CoachClientStatus.ARCHIVED
        scheduleService.releaseBookingsOf(coachId = coach.id, clientUserId = clientUserId)
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
