package app.trainer.backend.formcheck

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.media.MediaOwnerKind
import app.trainer.backend.media.PrepareUploadRequest
import app.trainer.backend.media.PrepareUploadResponse
import app.trainer.backend.traininglog.ExerciseRepository
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.data.domain.Limit
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val OWN_HISTORY_LIMIT = 50
private const val AWAITING_LIMIT = 20

@Service
class FormCheckService(
    private val formCheckRepository: FormCheckRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val exerciseRepository: ExerciseRepository,
    private val userRepository: UserRepository,
    private val mediaFileService: MediaFileService,
    private val clock: Clock,
) {

    @Transactional
    fun prepareUpload(clientUserId: UUID, request: PrepareUploadRequest): PrepareUploadResponse {
        val coach = requireOwnCoach(clientUserId)
        return mediaFileService.prepareUpload(
            uploaderUserId = clientUserId,
            ownerKind = MediaOwnerKind.FORM_CHECK,
            scopeId = coach.id,
            request = request,
        )
    }

    @Transactional
    fun create(clientUserId: UUID, request: CreateFormCheckRequest): FormCheckResponse {
        val coach = requireOwnCoach(clientUserId)
        val formCheck = FormCheckEntity(
            id = UUID.randomUUID(),
            clientUserId = clientUserId,
            coachId = coach.id,
            exerciseId = request.exerciseId,
            mediaFileId = request.mediaFileId,
            note = request.note?.trim()?.ifEmpty { null },
            coachComment = null,
            reviewedAt = null,
            reviewedByCoachId = null,
            createdAt = Instant.now(clock),
        )
        mediaFileService.link(
            mediaFileIds = listOf(request.mediaFileId),
            ownerKind = MediaOwnerKind.FORM_CHECK,
            ownerId = formCheck.id,
            scopeId = coach.id,
            uploaderUserId = clientUserId,
        )
        formCheckRepository.save(formCheck)
        return toResponse(formCheck)
    }

    @Transactional(readOnly = true)
    fun ownFormChecks(clientUserId: UUID): List<FormCheckResponse> {
        return formCheckRepository
            .findByClientUserIdOrderByCreatedAtDesc(
                clientUserId = clientUserId,
                limit = Limit.of(OWN_HISTORY_LIMIT),
            )
            .let(::toResponses)
    }

    @Transactional(readOnly = true)
    fun awaitingReview(coachUserId: UUID): List<FormCheckResponse> {
        val coach = requireCoach(coachUserId)
        return formCheckRepository
            .findByCoachIdAndReviewedAtIsNullOrderByCreatedAtDesc(
                coachId = coach.id,
                limit = Limit.of(AWAITING_LIMIT),
            )
            .let(::toResponses)
    }

    @Transactional
    fun review(coachUserId: UUID, formCheckId: UUID, request: ReviewFormCheckRequest): FormCheckResponse {
        val coach = requireCoach(coachUserId)
        val formCheck = formCheckRepository.findByIdOrNull(formCheckId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Разбор не найден")
        if (formCheck.coachId != coach.id) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Разбор не найден")
        }
        formCheck.coachComment = request.comment?.trim()?.ifEmpty { null }
        formCheck.reviewedAt = Instant.now(clock)
        formCheck.reviewedByCoachId = coach.id
        return toResponse(formCheck)
    }

    private fun requireCoach(coachUserId: UUID): CoachEntity = coachRepository.findByUserId(coachUserId)
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")

    private fun requireOwnCoach(clientUserId: UUID): CoachEntity {
        val link = coachClientRepository
            .findByUserId(clientUserId)
            .firstOrNull { it.status == CoachClientStatus.ACTIVE }
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "У вас пока нет тренера")
        return coachRepository.findByIdOrNull(link.coachId)
            ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Тренер не найден")
    }

    private fun toResponses(formChecks: List<FormCheckEntity>): List<FormCheckResponse> {
        if (formChecks.isEmpty()) return emptyList()
        val names = userRepository
            .findAllById(formChecks.map { it.clientUserId }.distinct())
            .associate { it.id to it.displayName }
        val exerciseNames = exerciseRepository
            .findAllById(formChecks.mapNotNull { it.exerciseId }.distinct())
            .associate { it.id to it.name }
        return formChecks.map { formCheck ->
            toResponse(
                formCheck = formCheck,
                clientDisplayName = names[formCheck.clientUserId].orEmpty(),
                exerciseName = formCheck.exerciseId?.let { exerciseNames[it] },
            )
        }
    }

    private fun toResponse(formCheck: FormCheckEntity): FormCheckResponse = toResponse(
        formCheck = formCheck,
        clientDisplayName = userRepository.findByIdOrNull(formCheck.clientUserId)?.displayName.orEmpty(),
        exerciseName = formCheck.exerciseId?.let { exerciseRepository.findByIdOrNull(it)?.name },
    )

    private fun toResponse(
        formCheck: FormCheckEntity,
        clientDisplayName: String,
        exerciseName: String?,
    ): FormCheckResponse = FormCheckResponse(
        id = formCheck.id,
        clientUserId = formCheck.clientUserId,
        clientDisplayName = clientDisplayName,
        exerciseId = formCheck.exerciseId,
        exerciseName = exerciseName,
        video = mediaFileService.findResponse(formCheck.mediaFileId),
        note = formCheck.note,
        coachComment = formCheck.coachComment,
        isReviewed = formCheck.reviewedAt != null,
        createdAt = formCheck.createdAt,
    )
}
