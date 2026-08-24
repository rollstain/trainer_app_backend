package app.trainer.backend.coach

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class CoachClientStatus { ACTIVE, ARCHIVED }

@Entity
@Table(name = "coach_clients")
class CoachClientEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: CoachClientStatus,

    @Column(name = "created_at")
    val createdAt: Instant,
)
