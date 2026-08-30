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
            where s.coach_id = :coachId
              and r.status = :status
              and (
                cast(:from as text) is null
                or s.starts_at between cast(:from as timestamptz) and cast(:to as timestamptz)
              )
              and (
                cast(:afterCreatedAt as text) is null
                or (r.created_at, r.id) > (cast(:afterCreatedAt as timestamptz), cast(:afterId as uuid))
              )
            order by r.created_at asc, r.id asc
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findByCoachIdAndStatusPage(
        @Param("coachId") coachId: UUID,
        @Param("status") status: String,
        @Param("from") from: String?,
        @Param("to") to: String?,
        @Param("afterCreatedAt") afterCreatedAt: String?,
        @Param("afterId") afterId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<SlotChangeRequestEntity>
}

interface SlotParticipantRepository : JpaRepository<SlotParticipantEntity, UUID> {

    @Query(
        value = """
            select p.user_id as clientUserId, s.starts_at as startsAt, s.status as status
            from slot_participants p
            join training_slots s on s.id = p.slot_id
            where s.coach_id = :coachId
              and s.starts_at between :from and :to
              and p.user_id = any (cast(:clientIds as uuid[]))
            order by p.user_id, s.starts_at desc
        """,
        nativeQuery = true,
    )
    fun findPastParticipation(
        @Param("coachId") coachId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("clientIds") clientIds: Array<UUID>,
    ): List<PastParticipation>

    fun findBySlotId(slotId: UUID): List<SlotParticipantEntity>

    fun findBySlotIdIn(slotIds: Collection<UUID>): List<SlotParticipantEntity>

    fun findBySlotIdAndUserId(slotId: UUID, userId: UUID): SlotParticipantEntity?

    fun countBySlotId(slotId: UUID): Int

    fun deleteBySlotIdAndUserId(slotId: UUID, userId: UUID)
}

interface PastParticipation {

    fun getClientUserId(): UUID

    fun getStartsAt(): Instant

    fun getStatus(): String
}
