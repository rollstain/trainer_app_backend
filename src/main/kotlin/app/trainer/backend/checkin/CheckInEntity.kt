package app.trainer.backend.checkin

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

    @Column(name = "adherence")
    var adherence: Int?,

    @Column(name = "coach_comment")
    var coachComment: String?,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant?,

    @Column(name = "reviewed_by_coach_id")
    var reviewedByCoachId: UUID?,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "updated_at")
    var updatedAt: Instant,
)

interface CheckInRepository : JpaRepository<CheckInEntity, UUID> {

    @Query(
        value = """
            select c.* from check_ins c
            join coach_clients l on l.user_id = c.client_user_id
            where l.coach_id = :coachId
              and l.status = 'ACTIVE'
              and c.reviewed_at is null
              and (
                cast(:afterCheckInDate as text) is null
                or (c.check_in_date, c.id) < (cast(:afterCheckInDate as date), cast(:afterId as uuid))
              )
            order by c.check_in_date desc, c.id desc
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findAwaitingPage(
        @Param("coachId") coachId: UUID,
        @Param("afterCheckInDate") afterCheckInDate: String?,
        @Param("afterId") afterId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<CheckInEntity>

    fun findByClientUserIdAndCheckInDate(clientUserId: UUID, checkInDate: LocalDate): CheckInEntity?

    fun findByClientUserIdAndCheckInDateBetweenOrderByCheckInDateDesc(
        clientUserId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<CheckInEntity>
}
