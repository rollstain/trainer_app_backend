package app.trainer.backend.traininglog

import java.time.LocalDate
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ExerciseRepository : JpaRepository<ExerciseEntity, UUID> {

    @Query(
        value = """
            select e.* from exercises e
            where e.archived_at is null
              and (e.coach_id is null or e.coach_id = any (cast(:coachIds as uuid[])))
            order by e.name
        """,
        nativeQuery = true,
    )
    fun findAvailable(@Param("coachIds") coachIds: Array<UUID>): List<ExerciseEntity>

    fun findByCoachIdAndArchivedAtIsNull(coachId: UUID): List<ExerciseEntity>
}

interface TrainingLogEntryRepository : JpaRepository<TrainingLogEntryEntity, UUID> {

    fun findByClientUserIdAndEntryDate(clientUserId: UUID, entryDate: LocalDate): TrainingLogEntryEntity?

    fun findByClientUserIdAndEntryDateBetweenOrderByEntryDateDesc(
        clientUserId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<TrainingLogEntryEntity>
}

interface ExerciseBestVolume {

    fun getExerciseId(): UUID

    fun getBestVolume(): Long
}

interface TrainingLogSetRepository : JpaRepository<TrainingLogSetEntity, UUID> {

    @Query(
        value = """
            select distinct on (s.exercise_id) s.*
            from training_log_sets s
            join training_log_entries e on e.id = s.entry_id
            where e.client_user_id = :clientUserId
            order by s.exercise_id, e.entry_date desc, s.position desc
        """,
        nativeQuery = true,
    )
    fun findLatestPerExercise(@Param("clientUserId") clientUserId: UUID): List<TrainingLogSetEntity>

    @Query(
        value = """
            select s.exercise_id as exerciseId,
                   max(s.repetitions * s.weight_grams) as bestVolume
            from training_log_sets s
            join training_log_entries e on e.id = s.entry_id
            where e.client_user_id = :clientUserId
              and s.repetitions is not null
              and s.weight_grams is not null
              and e.entry_date < :beforeDate
            group by s.exercise_id
        """,
        nativeQuery = true,
    )
    fun bestVolumePerExerciseBefore(
        @Param("clientUserId") clientUserId: UUID,
        @Param("beforeDate") beforeDate: LocalDate,
    ): List<ExerciseBestVolume>

    fun findByEntryIdInOrderByPositionAsc(entryIds: Collection<UUID>): List<TrainingLogSetEntity>

    fun deleteByEntryId(entryId: UUID)
}
