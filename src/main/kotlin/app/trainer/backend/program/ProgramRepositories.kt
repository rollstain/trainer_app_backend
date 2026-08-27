package app.trainer.backend.program

import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository

interface TrainingProgramRepository : JpaRepository<TrainingProgramEntity, UUID> {

    fun findByCoachIdAndArchivedAtIsNullOrderByCreatedAtDesc(coachId: UUID): List<TrainingProgramEntity>
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

    fun findByCoachIdAndEndedAtIsNull(coachId: UUID): List<ProgramAssignmentEntity>

    fun findByProgramIdAndEndedAtIsNull(programId: UUID): List<ProgramAssignmentEntity>
}
