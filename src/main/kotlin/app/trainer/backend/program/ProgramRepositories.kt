package app.trainer.backend.program

import java.time.Instant
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProgramSummaryRow {

    fun getProgramId(): UUID

    fun getTitle(): String

    fun getWeeksCount(): Int

    fun getFilledDaysCount(): Long

    fun getAssignedClientsCount(): Long

    fun getCreatedAt(): Instant
}

interface TrainingProgramRepository : JpaRepository<TrainingProgramEntity, UUID> {

    @Query(
        value = """
            select p.id as programId,
                   p.title as title,
                   p.weeks_count as weeksCount,
                   p.created_at as createdAt,
                   (
                     select count(distinct pe.program_day_id)
                     from program_exercises pe
                     join program_days pd on pd.id = pe.program_day_id
                     where pd.program_id = p.id
                   ) as filledDaysCount,
                   (
                     select count(*) from program_assignments pa
                     where pa.program_id = p.id and pa.ended_at is null
                   ) as assignedClientsCount
            from training_programs p
            where p.coach_id = :coachId
              and p.archived_at is null
              and (
                cast(:afterCreatedAt as text) is null
                or (p.created_at, p.id) < (cast(:afterCreatedAt as timestamptz), cast(:afterId as uuid))
              )
            order by p.created_at desc, p.id desc
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findPage(
        @Param("coachId") coachId: UUID,
        @Param("afterCreatedAt") afterCreatedAt: String?,
        @Param("afterId") afterId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<ProgramSummaryRow>
}

interface ProgramDayRepository : JpaRepository<ProgramDayEntity, UUID> {

    fun findByProgramIdOrderByWeekNumberAscDayOfWeekAsc(programId: UUID): List<ProgramDayEntity>

    fun findByProgramIdAndWeekNumberAndDayOfWeek(
        programId: UUID,
        weekNumber: Int,
        dayOfWeek: Int,
    ): ProgramDayEntity?
}

interface ProgramExerciseRepository : JpaRepository<ProgramExerciseEntity, UUID> {

    fun findByProgramDayIdInOrderByPositionAsc(programDayIds: Collection<UUID>): List<ProgramExerciseEntity>

    fun deleteByProgramDayId(programDayId: UUID)
}

interface ProgramAssignmentRepository : JpaRepository<ProgramAssignmentEntity, UUID> {

    fun findByClientUserIdAndEndedAtIsNull(clientUserId: UUID): ProgramAssignmentEntity?

    fun findByProgramIdAndEndedAtIsNull(programId: UUID): List<ProgramAssignmentEntity>
}
