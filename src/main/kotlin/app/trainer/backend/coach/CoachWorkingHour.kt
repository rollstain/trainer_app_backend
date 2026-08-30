package app.trainer.backend.coach

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalTime
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

@Entity
@Table(name = "coach_working_hours")
class CoachWorkingHourEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "day_of_week")
    val dayOfWeek: Int,

    @Column(name = "opens_at")
    val opensAt: LocalTime,

    @Column(name = "closes_at")
    val closesAt: LocalTime,
)

interface CoachWorkingHourRepository : JpaRepository<CoachWorkingHourEntity, UUID> {

    fun findByCoachIdOrderByDayOfWeek(coachId: UUID): List<CoachWorkingHourEntity>

    @Modifying
    @Query("delete from CoachWorkingHourEntity hour where hour.coachId = :coachId")
    fun deleteAllOfCoach(coachId: UUID)
}
