package app.trainer.backend.habit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

@Entity
@Table(name = "habits")
class HabitEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "coach_id")
    val coachId: UUID?,

    @Column(name = "client_user_id")
    val clientUserId: UUID,

    @Column(name = "title")
    var title: String,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "archived_at")
    var archivedAt: Instant?,

    @Column(name = "habit_set_id")
    val habitSetId: UUID?,
)

@Entity
@Table(name = "habit_marks")
class HabitMarkEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "habit_id")
    val habitId: UUID,

    @Column(name = "mark_date")
    val markDate: LocalDate,
)

interface HabitRepository : JpaRepository<HabitEntity, UUID> {

    fun findByClientUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(clientUserId: UUID): List<HabitEntity>

    @Query("select distinct h.title from HabitEntity h where h.coachId = :coachId order by h.title")
    fun titlesOfCoach(@Param("coachId") coachId: UUID): List<String>

    @Query(
        "select distinct h.habitSetId as setId, h.clientUserId as clientUserId from HabitEntity h " +
            "where h.habitSetId in :setIds"
    )
    fun clientsOfSets(@Param("setIds") setIds: Collection<UUID>): List<HabitSetClient>
}

interface HabitSetClient {
    val setId: UUID
    val clientUserId: UUID
}

interface HabitMarkRepository : JpaRepository<HabitMarkEntity, UUID> {

    fun findByHabitIdInAndMarkDateBetween(
        habitIds: Collection<UUID>,
        from: LocalDate,
        to: LocalDate,
    ): List<HabitMarkEntity>

    fun findByHabitIdAndMarkDate(habitId: UUID, markDate: LocalDate): HabitMarkEntity?
}
