package app.trainer.backend.checkin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "check_ins")
class CheckInEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "client_user_id")
    val clientUserId: UUID,

    @Column(name = "check_in_date")
    val checkInDate: LocalDate,

    @Column(name = "weight_grams")
    var weightGrams: Int?,

    @Column(name = "waist_mm")
    var waistMillimeters: Int?,

    @Column(name = "chest_mm")
    var chestMillimeters: Int?,

    @Column(name = "hips_mm")
    var hipsMillimeters: Int?,

    @Column(name = "wellbeing")
    var wellbeing: Int?,

    @Column(name = "sleep_quality")
    var sleepQuality: Int?,

    @Column(name = "notes")
    var notes: String?,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "updated_at")
    var updatedAt: Instant,
)

interface CheckInRepository : JpaRepository<CheckInEntity, UUID> {

    fun findByClientUserIdAndCheckInDate(clientUserId: UUID, checkInDate: LocalDate): CheckInEntity?

    fun findByClientUserIdAndCheckInDateBetweenOrderByCheckInDateDesc(
        clientUserId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<CheckInEntity>
}
