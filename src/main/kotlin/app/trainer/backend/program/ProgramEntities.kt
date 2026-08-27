package app.trainer.backend.program

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "training_programs")
class TrainingProgramEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "title")
    var title: String,

    @Column(name = "weeks_count")
    var weeksCount: Int,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "archived_at")
    var archivedAt: Instant?,
)

@Entity
@Table(name = "program_days")
class ProgramDayEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "program_id")
    val programId: UUID,

    @Column(name = "week_number")
    val weekNumber: Int,

    @Column(name = "day_of_week")
    val dayOfWeek: Int,

    @Column(name = "title")
    var title: String,
)

@Entity
@Table(name = "program_exercises")
class ProgramExerciseEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "program_day_id")
    val programDayId: UUID,

    @Column(name = "exercise_id")
    val exerciseId: UUID,

    @Column(name = "position")
    val position: Int,

    @Column(name = "sets_count")
    val setsCount: Int,

    @Column(name = "repetitions")
    val repetitions: Int?,

    @Column(name = "weight_grams")
    val weightGrams: Int?,

    @Column(name = "rest_seconds")
    val restSeconds: Int?,

    @Column(name = "note")
    val note: String?,
)

@Entity
@Table(name = "program_assignments")
class ProgramAssignmentEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "program_id")
    val programId: UUID,

    @Column(name = "coach_id")
    val coachId: UUID,

    @Column(name = "client_user_id")
    val clientUserId: UUID,

    @Column(name = "starts_on")
    val startsOn: LocalDate,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "ended_at")
    var endedAt: Instant?,
)
