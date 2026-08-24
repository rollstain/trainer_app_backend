package app.trainer.backend.clientnotes

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface ClientNoteRepository : JpaRepository<ClientNoteEntity, UUID> {

    fun findByCoachIdAndClientUserIdAndArchivedAtIsNull(
        coachId: UUID,
        clientUserId: UUID,
    ): List<ClientNoteEntity>

    fun findByCoachIdAndArchivedAtIsNullAndIsPinnedIsTrue(coachId: UUID): List<ClientNoteEntity>
}
