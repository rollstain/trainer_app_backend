package app.trainer.backend.schedule

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

@Entity
@Table(name = "slot_waitlist")
class SlotWaitlistEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "slot_id")
    val slotId: UUID,

    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "notified_at")
    var notifiedAt: Instant?,
)

interface SlotWaitlistRepository : JpaRepository<SlotWaitlistEntity, UUID> {

    fun findBySlotIdOrderByCreatedAtAsc(slotId: UUID): List<SlotWaitlistEntity>

    fun findBySlotIdAndUserId(slotId: UUID, userId: UUID): SlotWaitlistEntity?

    fun findBySlotIdInAndUserId(slotIds: Collection<UUID>, userId: UUID): List<SlotWaitlistEntity>

    fun deleteBySlotId(slotId: UUID)
}
