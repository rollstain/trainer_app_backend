package app.trainer.backend.traininglog

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ExerciseKind { STRENGTH, CARDIO, BODYWEIGHT }

@Entity
@Table(name = "exercises")
class ExerciseEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "coach_id")
    val coachId: UUID?,

    @Column(name = "name")
    var name: String,

    @Column(name = "muscle_group")
    var muscleGroup: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    var kind: ExerciseKind,

    @Column(name = "description")
    var description: String?,

    @Column(name = "video_url")
    var videoUrl: String?,

    @Column(name = "video_media_file_id")
    var videoMediaFileId: UUID?,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "archived_at")
    var archivedAt: Instant?,
)

@Entity
@Table(name = "training_log_entries")
class TrainingLogEntryEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "client_user_id")
    val clientUserId: UUID,

    @Column(name = "entry_date")
    val entryDate: LocalDate,

    @Column(name = "slot_id")
    var slotId: UUID?,

    @Column(name = "notes")
    var notes: String?,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "updated_at")
    var updatedAt: Instant,
)

@Entity
@Table(name = "training_log_sets")
class TrainingLogSetEntity(

    @Id
    @Column(name = "id")
    val id: UUID,

    @Column(name = "entry_id")
    val entryId: UUID,

    @Column(name = "exercise_id")
    val exerciseId: UUID,

    @Column(name = "position")
    val position: Int,

    @Column(name = "repetitions")
    val repetitions: Int?,

    @Column(name = "weight_grams")
    val weightGrams: Int?,

    @Column(name = "duration_seconds")
    val durationSeconds: Int?,

    @Column(name = "distance_meters")
    val distanceMeters: Int?,
)
