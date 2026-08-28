package app.trainer.backend.schedule

import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TrainingSlotRepository : JpaRepository<TrainingSlotEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: UUID): TrainingSlotEntity?

    fun findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(
        coachId: UUID,
        from: Instant,
        to: Instant,
    ): List<TrainingSlotEntity>

    @Query(
        value = """
            select s.* from training_slots s
            join slot_participants p on p.slot_id = s.id
            where p.user_id = :userId
              and s.starts_at between :from and :to
            order by s.starts_at
        """,
        nativeQuery = true,
    )
    fun findParticipatedBetween(
        @Param("userId") userId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
    ): List<TrainingSlotEntity>

    fun findByStartsAtBetweenOrderByStartsAtAsc(from: Instant, to: Instant): List<TrainingSlotEntity>

    @Query(
        value = """
            select s.* from training_slots s
            join slot_participants p on p.slot_id = s.id
            where s.coach_id = :coachId
              and p.user_id = :userId
              and s.starts_at > :startsAt
            order by s.starts_at
        """,
        nativeQuery = true,
    )
    fun findParticipatedAfter(
        @Param("coachId") coachId: UUID,
        @Param("userId") userId: UUID,
        @Param("startsAt") startsAt: Instant,
    ): List<TrainingSlotEntity>

    @Query(
        value = """
            select s.id from training_slots s
            where s.coach_id = :coachId
              and s.status <> 'CANCELLED'
              and s.starts_at < :endsAt
              and s.starts_at + (s.duration_minutes * interval '1 minute') > :startsAt
        """,
        nativeQuery = true,
    )
    fun findOverlappingSlotIds(
        @Param("coachId") coachId: UUID,
        @Param("startsAt") startsAt: Instant,
        @Param("endsAt") endsAt: Instant,
    ): List<UUID>
}

interface SlotChangeRequestRepository : JpaRepository<SlotChangeRequestEntity, UUID> {

    fun findBySlotIdAndStatus(slotId: UUID, status: SlotChangeStatus): SlotChangeRequestEntity?

    fun findBySlotIdInAndStatus(slotIds: Collection<UUID>, status: SlotChangeStatus): List<SlotChangeRequestEntity>

    @Query(
        value = """
            select r.* from slot_change_requests r
            join training_slots s on s.id = r.slot_id
            where s.coach_id = :coachId and r.status = :status
            order by r.created_at asc
        """,
        nativeQuery = true,
    )
    fun findByCoachIdAndStatus(
        @Param("coachId") coachId: UUID,
        @Param("status") status: String,
    ): List<SlotChangeRequestEntity>
}

interface SlotParticipantRepository : JpaRepository<SlotParticipantEntity, UUID> {

    fun findBySlotId(slotId: UUID): List<SlotParticipantEntity>

    fun findBySlotIdIn(slotIds: Collection<UUID>): List<SlotParticipantEntity>

    fun findBySlotIdAndUserId(slotId: UUID, userId: UUID): SlotParticipantEntity?

    fun countBySlotId(slotId: UUID): Int

    fun deleteBySlotIdAndUserId(slotId: UUID, userId: UUID)
}
