package app.trainer.backend.clientnotes

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ClientNoteRepository : JpaRepository<ClientNoteEntity, UUID> {

    fun findByCoachIdAndClientUserIdAndArchivedAtIsNull(
        coachId: UUID,
        clientUserId: UUID,
    ): List<ClientNoteEntity>

    fun findByCoachIdAndArchivedAtIsNullAndIsPinnedIsTrue(coachId: UUID): List<ClientNoteEntity>

    @Query(
        "SELECT DISTINCT note.clientUserId FROM ClientNoteEntity note " +
            "WHERE note.coachId = :coachId AND note.archivedAt IS NULL AND note.kind = :kind"
    )
    fun findClientUserIdsWithKind(
        @Param("coachId") coachId: UUID,
        @Param("kind") kind: ClientNoteKind,
    ): List<UUID>
}
