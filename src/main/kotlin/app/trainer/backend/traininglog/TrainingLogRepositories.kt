package app.trainer.backend.traininglog

import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ExerciseRepository : JpaRepository<ExerciseEntity, UUID> {

    @Query(
        value = """
            select e.* from exercises e
            where e.archived_at is null
              and (e.owner_kind = 'SHARED' or e.owner_id = any (cast(:ownerIds as uuid[])))
            order by e.name
        """,
        nativeQuery = true,
    )
    fun findAvailable(@Param("ownerIds") ownerIds: Array<UUID>): List<ExerciseEntity>

    @Query(
        value = """
            select e.* from exercises e
            where e.archived_at is null
              and (e.owner_kind = 'SHARED' or e.owner_id = any (cast(:ownerIds as uuid[])))
              and (cast(:search as text) is null or e.name ilike '%' || cast(:search as text) || '%')
              and (cast(:muscles as text[]) is null or e.primary_muscle = any (cast(:muscles as text[])))
              and (cast(:equipment as text[]) is null or e.equipment = any (cast(:equipment as text[])))
              and (cast(:ownerKind as text) is null or e.owner_kind = cast(:ownerKind as text))
              and (
                cast(:afterName as text) is null
                or (e.name, e.id) > (cast(:afterName as text), cast(:afterId as uuid))
              )
            order by e.name, e.id
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findAvailablePage(
        @Param("ownerIds") ownerIds: Array<UUID>,
        @Param("search") search: String?,
        @Param("muscles") muscles: Array<String>?,
        @Param("equipment") equipment: Array<String>?,
        @Param("ownerKind") ownerKind: String?,
        @Param("afterName") afterName: String?,
        @Param("afterId") afterId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<ExerciseEntity>

    fun findByOwnerIdAndArchivedAtIsNull(ownerId: UUID): List<ExerciseEntity>

    @Query(
        value = """
            select pe.exercise_id as exerciseId, count(distinct pd.program_id) as programsCount
            from program_exercises pe
            join program_days pd on pd.id = pe.program_day_id
            join training_programs tp on tp.id = pd.program_id
            where tp.coach_id = :coachId
              and tp.archived_at is null
              and pe.exercise_id = any (cast(:exerciseIds as uuid[]))
            group by pe.exercise_id
        """,
        nativeQuery = true,
    )
    fun countProgramsUsing(
        @Param("coachId") coachId: UUID,
        @Param("exerciseIds") exerciseIds: Array<UUID>,
    ): List<ExerciseUsage>
}

interface ExerciseUsage {

    fun getExerciseId(): UUID

    fun getProgramsCount(): Long
}

interface ClientDiaryDay {

    fun getClientUserId(): UUID

    fun getEntryDate(): LocalDate

    fun getVolumeGrams(): Long
}

interface ClientLastEntry {

    fun getClientUserId(): UUID

    fun getLastEntryDate(): LocalDate
}

interface TrainingLogEntryRepository : JpaRepository<TrainingLogEntryEntity, UUID> {

    @Query(
        value = """
            select e.* from training_log_entries e
            join coach_clients l on l.user_id = e.client_user_id
            left join coach_training_log_views v on v.entry_id = e.id and v.coach_id = :coachId
            where l.coach_id = :coachId
              and l.status = 'ACTIVE'
              and exists (select 1 from training_log_sets s where s.entry_id = e.id)
              and (:unseenOnly = false or v.seen_at is null or v.seen_at < e.updated_at)
              and (
                cast(:afterUpdatedAt as text) is null
                or (e.updated_at, e.id) < (cast(:afterUpdatedAt as timestamptz), cast(:afterId as uuid))
              )
            order by e.updated_at desc, e.id desc
            limit :pageSize
        """,
        nativeQuery = true,
    )
    fun findFeedPage(
        @Param("coachId") coachId: UUID,
        @Param("unseenOnly") unseenOnly: Boolean,
        @Param("afterUpdatedAt") afterUpdatedAt: String?,
        @Param("afterId") afterId: UUID?,
        @Param("pageSize") pageSize: Int,
    ): List<TrainingLogEntryEntity>

    @Query(
        value = """
            select count(*) from training_log_entries e
            join coach_clients l on l.user_id = e.client_user_id
            left join coach_training_log_views v on v.entry_id = e.id and v.coach_id = :coachId
            where l.coach_id = :coachId
              and l.status = 'ACTIVE'
              and exists (select 1 from training_log_sets s where s.entry_id = e.id)
              and (v.seen_at is null or v.seen_at < e.updated_at)
        """,
        nativeQuery = true,
    )
    fun countUnseen(@Param("coachId") coachId: UUID): Long

    @Query(
        value = """
            select v.entry_id from coach_training_log_views v
            join training_log_entries e on e.id = v.entry_id
            where v.coach_id = :coachId
              and v.entry_id in (:entryIds)
              and v.seen_at >= e.updated_at
        """,
        nativeQuery = true,
    )
    fun findSeenEntryIds(
        @Param("coachId") coachId: UUID,
        @Param("entryIds") entryIds: Collection<UUID>,
    ): List<UUID>

    @Query(
        value = """
            select exists (
              select 1 from training_log_entries e
              join coach_clients l on l.user_id = e.client_user_id
              where e.id = :entryId
                and l.coach_id = :coachId
                and l.status = 'ACTIVE'
            )
        """,
        nativeQuery = true,
    )
    fun isEntryOfActiveClient(@Param("coachId") coachId: UUID, @Param("entryId") entryId: UUID): Boolean

    @Modifying
    @Query(
        value = """
            insert into coach_training_log_views (coach_id, entry_id, seen_at)
            values (:coachId, :entryId, :seenAt)
            on conflict (coach_id, entry_id) do update set seen_at = excluded.seen_at
        """,
        nativeQuery = true,
    )
    fun markSeen(
        @Param("coachId") coachId: UUID,
        @Param("entryId") entryId: UUID,
        @Param("seenAt") seenAt: Instant,
    )

    fun findByClientUserIdAndEntryDate(clientUserId: UUID, entryDate: LocalDate): TrainingLogEntryEntity?

    fun findByClientUserIdAndEntryDateBetweenOrderByEntryDateDesc(
        clientUserId: UUID,
        from: LocalDate,
        to: LocalDate,
    ): List<TrainingLogEntryEntity>

    @Query(
        value = """
            select e.client_user_id as clientUserId,
                   e.entry_date as entryDate,
                   coalesce(sum(s.repetitions * s.weight_grams), 0) as volumeGrams
            from training_log_entries e
            left join training_log_sets s
              on s.entry_id = e.id and s.repetitions is not null and s.weight_grams is not null
            where e.client_user_id = any (cast(:clientIds as uuid[]))
              and e.entry_date between :from and :to
            group by e.client_user_id, e.entry_date
            order by e.entry_date
        """,
        nativeQuery = true,
    )
    fun findDiaryDays(
        @Param("clientIds") clientIds: Array<UUID>,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<ClientDiaryDay>

    @Query(
        value = """
            select e.client_user_id as clientUserId,
                   max(e.entry_date) as lastEntryDate
            from training_log_entries e
            where e.client_user_id = any (cast(:clientIds as uuid[]))
            group by e.client_user_id
        """,
        nativeQuery = true,
    )
    fun findLastEntryDates(@Param("clientIds") clientIds: Array<UUID>): List<ClientLastEntry>
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
