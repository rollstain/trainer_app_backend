package app.trainer.backend.schedule

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class SlotStatus { FREE, BOOKED, CANCELLED, COMPLETED }

enum class SlotChangeKind { RESCHEDULE, CANCEL }

enum class SlotChangeStatus { PENDING, APPROVED, REJECTED }

@Entity
@Table(name = "training_slots")
class TrainingSlotEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "starts_at")
    var startsAt: Instant,

    @Column(name = "duration_minutes")
    var durationMinutes: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: SlotStatus,

    @Column(name = "client_user_id")
    var clientUserId: UUID?,

    @Column(name = "created_at")
    val createdAt: Instant,
)

@Entity
@Table(name = "slot_change_requests")
class SlotChangeRequestEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "slot_id")
    val slotId: UUID,

    @Column(name = "requested_by_user_id")
    val requestedByUserId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    val kind: SlotChangeKind,

    @Column(name = "proposed_starts_at")
    val proposedStartsAt: Instant?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: SlotChangeStatus,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "resolved_at")
    var resolvedAt: Instant?,
)
