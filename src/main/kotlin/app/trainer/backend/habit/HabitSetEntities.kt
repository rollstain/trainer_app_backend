package app.trainer.backend.habit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "habit_sets")
class HabitSetEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "title")
    var title: String,

    @Column(name = "created_at")
    val createdAt: Instant,
)

@Entity
@Table(name = "habit_set_items")
class HabitSetItemEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "set_id")
    val setId: UUID,

    @Column(name = "position")
    val position: Int,

    @Column(name = "title")
    val title: String,
)

interface HabitSetRepository : JpaRepository<HabitSetEntity, UUID> {

    fun findByCoachIdOrderByCreatedAtAsc(coachId: UUID): List<HabitSetEntity>
}

interface HabitSetItemRepository : JpaRepository<HabitSetItemEntity, UUID> {

    fun findBySetIdInOrderByPositionAsc(setIds: Collection<UUID>): List<HabitSetItemEntity>

    fun deleteBySetId(setId: UUID)
}
