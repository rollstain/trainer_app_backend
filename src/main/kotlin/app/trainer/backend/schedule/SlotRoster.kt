package app.trainer.backend.schedule

import app.trainer.backend.clientnotes.ClientNoteKind
import app.trainer.backend.clientnotes.ClientNoteRepository
import app.trainer.backend.user.UserRepository
import java.util.UUID
import org.springframework.stereotype.Component

@Component
class SlotRoster(
    private val participantRepository: SlotParticipantRepository,
    private val waitlistRepository: SlotWaitlistRepository,
    private val clientNoteRepository: ClientNoteRepository,
    private val userRepository: UserRepository,
) {

    fun participantsOf(slots: List<TrainingSlotEntity>): Map<UUID, List<SlotParticipantResponse>> {
        val participants = participantRepository.findBySlotIdIn(slots.map { it.id })
        val names = namesOf(participants.map { it.userId })
        val medical = medicalNoteUserIdsOf(slots.firstOrNull()?.coachId)
        return participants
            .groupBy { it.slotId }
            .mapValues { (_, rows) -> rows.map { row -> participantOf(row = row, names = names, medical = medical) } }
    }

    fun participantsOf(slot: TrainingSlotEntity): List<SlotParticipantResponse> {
        val participants = participantRepository.findBySlotId(slot.id)
        val names = namesOf(participants.map { it.userId })
        val medical = medicalNoteUserIdsOf(slot.coachId)
        return participants.map { row -> participantOf(row = row, names = names, medical = medical) }
    }

    fun waitlistOf(slots: List<TrainingSlotEntity>): Map<UUID, List<SlotWaitlistResponse>> = slots
        .flatMap { waitlistRepository.findBySlotIdOrderByCreatedAtAsc(it.id) }
        .let { waiting ->
            val names = namesOf(waiting.map { it.userId })
            waiting
                .groupBy { it.slotId }
                .mapValues { (_, rows) -> rows.map { row -> waitingOf(row = row, names = names) } }
        }

    fun waitlistOf(slotId: UUID): List<SlotWaitlistResponse> {
        val waiting = waitlistRepository.findBySlotIdOrderByCreatedAtAsc(slotId)
        val names = namesOf(waiting.map { it.userId })
        return waiting.map { row -> waitingOf(row = row, names = names) }
    }

    private fun participantOf(
        row: SlotParticipantEntity,
        names: Map<UUID, String>,
        medical: Set<UUID>,
    ): SlotParticipantResponse = SlotParticipantResponse(
        userId = row.userId,
        displayName = names[row.userId],
        bookedAt = row.createdAt,
        hasMedicalNotes = medical.contains(row.userId),
    )

    private fun waitingOf(row: SlotWaitlistEntity, names: Map<UUID, String>): SlotWaitlistResponse =
        SlotWaitlistResponse(
            userId = row.userId,
            displayName = names[row.userId],
            joinedAt = row.createdAt,
        )

    private fun medicalNoteUserIdsOf(coachId: UUID?): Set<UUID> {
        if (coachId == null) return emptySet()
        return clientNoteRepository
            .findClientUserIdsWithKind(coachId = coachId, kind = ClientNoteKind.MEDICAL)
            .toSet()
    }

    private fun namesOf(userIds: List<UUID>): Map<UUID, String> = userRepository
        .findAllById(userIds.distinct())
        .associate { it.id to it.displayName }
}
