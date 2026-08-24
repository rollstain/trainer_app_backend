package app.trainer.backend.clientnotes

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import java.time.Clock
import java.time.Instant
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ClientNoteService(
    private val noteRepository: ClientNoteRepository,
    private val coachRepository: CoachRepository,
    private val coachClientRepository: CoachClientRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun notesOfClient(coachUserId: UUID, clientUserId: UUID): List<ClientNoteResponse> {
        val coach = requireCoach(coachUserId)
        requireActiveClient(coach = coach, clientUserId = clientUserId)
        return noteRepository
            .findByCoachIdAndClientUserIdAndArchivedAtIsNull(coachId = coach.id, clientUserId = clientUserId)
            .sortedWith(byImportance())
            .map(::toResponse)
    }

    @Transactional(readOnly = true)
    fun pinnedNotes(coachUserId: UUID): List<ClientNoteResponse> {
        val coach = requireCoach(coachUserId)
        return noteRepository
            .findByCoachIdAndArchivedAtIsNullAndIsPinnedIsTrue(coachId = coach.id)
            .sortedWith(byImportance())
            .map(::toResponse)
    }

    @Transactional
    fun create(coachUserId: UUID, clientUserId: UUID, request: CreateClientNoteRequest): ClientNoteResponse {
        val coach = requireCoach(coachUserId)
        requireActiveClient(coach = coach, clientUserId = clientUserId)
        val now = Instant.now(clock)
        val note = noteRepository.save(
            ClientNoteEntity(
                id = UUID.randomUUID(),
                coachId = coach.id,
                clientUserId = clientUserId,
                kind = request.kind,
                title = request.title.trim(),
                details = normalizeDetails(request.details),
                isPinned = request.isPinned,
                createdAt = now,
                updatedAt = now,
                archivedAt = null,
            )
        )
        return toResponse(note)
    }

    @Transactional
    fun update(coachUserId: UUID, noteId: UUID, request: UpdateClientNoteRequest): ClientNoteResponse {
        val note = requireOwnActiveNote(coachUserId = coachUserId, noteId = noteId)
        note.kind = request.kind
        note.title = request.title.trim()
        note.details = normalizeDetails(request.details)
        note.isPinned = request.isPinned
        note.updatedAt = Instant.now(clock)
        return toResponse(note)
    }

    @Transactional
    fun archive(coachUserId: UUID, noteId: UUID) {
        val note = requireOwnActiveNote(coachUserId = coachUserId, noteId = noteId)
        val now = Instant.now(clock)
        note.archivedAt = now
        note.updatedAt = now
    }

    private fun normalizeDetails(raw: String?): String? {
        val trimmed = raw?.trim()
        return if (trimmed.isNullOrEmpty()) null else trimmed
    }

    private fun byImportance(): Comparator<ClientNoteEntity> {
        return compareByDescending<ClientNoteEntity> { it.isPinned }
            .thenByDescending { it.kind == ClientNoteKind.MEDICAL }
            .thenByDescending { it.createdAt }
    }

    private fun requireOwnActiveNote(coachUserId: UUID, noteId: UUID): ClientNoteEntity {
        val coach = requireCoach(coachUserId)
        val note = noteRepository.findByIdOrNull(noteId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пометка не найдена")
        if (note.coachId != coach.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пометка другого тренера")
        }
        if (note.archivedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Пометка в архиве")
        }
        return note
    }

    private fun requireCoach(coachUserId: UUID): CoachEntity = coachRepository.findByUserId(coachUserId)
        ?: throw ResponseStatusException(HttpStatus.FORBIDDEN, "Пользователь не тренер")

    private fun requireActiveClient(coach: CoachEntity, clientUserId: UUID) {
        val link = coachClientRepository.findByCoachIdAndUserId(coachId = coach.id, userId = clientUserId)
        if (link == null || link.status != CoachClientStatus.ACTIVE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваш подопечный")
        }
    }

    private fun toResponse(note: ClientNoteEntity): ClientNoteResponse = ClientNoteResponse(
        id = note.id,
        clientUserId = note.clientUserId,
        kind = note.kind,
        title = note.title,
        details = note.details,
        isPinned = note.isPinned,
        createdAt = note.createdAt,
        updatedAt = note.updatedAt,
    )
}
