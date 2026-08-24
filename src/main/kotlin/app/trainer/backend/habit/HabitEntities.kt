package app.trainer.backend.habit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

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
}

interface HabitMarkRepository : JpaRepository<HabitMarkEntity, UUID> {

    fun findByHabitIdInAndMarkDateBetween(
        habitIds: Collection<UUID>,
        from: LocalDate,
        to: LocalDate,
    ): List<HabitMarkEntity>

    fun findByHabitIdAndMarkDate(habitId: UUID, markDate: LocalDate): HabitMarkEntity?
}
