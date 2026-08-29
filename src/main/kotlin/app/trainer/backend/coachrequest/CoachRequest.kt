package app.trainer.backend.coachrequest

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

enum class CoachRequestStatus { PENDING, APPROVED, DECLINED }

@Entity
@Table(name = "coach_requests")
class CoachRequestEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "about")
    var about: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: CoachRequestStatus,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "decided_at")
    var decidedAt: Instant?,
)

interface CoachRequestRepository : JpaRepository<CoachRequestEntity, UUID> {

    fun findByUserId(userId: UUID): CoachRequestEntity?

    fun findByStatusOrderByCreatedAtAsc(status: CoachRequestStatus): List<CoachRequestEntity>

    fun findByStatusNotOrderByDecidedAtDesc(status: CoachRequestStatus): List<CoachRequestEntity>
}
