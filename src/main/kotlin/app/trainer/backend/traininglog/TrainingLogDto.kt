package app.trainer.backend.traininglog

import app.trainer.backend.media.MediaFileResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val EXERCISE_NAME_MAX_LENGTH = 120
private const val NOTES_MAX_LENGTH = 2000
private const val SETS_MAX_COUNT = 200

private const val EXERCISE_DESCRIPTION_MAX_LENGTH = 2000
private const val EXERCISE_VIDEO_URL_MAX_LENGTH = 500

data class ExerciseResponse(
    val id: UUID,
    val name: String,
    val primaryMuscle: MuscleGroup?,
    val equipment: Equipment?,
    val kind: ExerciseKind,
    val ownerKind: ExerciseOwnerKind,
    val ownerDisplayName: String?,
    val description: String?,
    val videoUrl: String?,
    val video: MediaFileResponse?,
    val lastRepetitions: Int?,
    val lastWeightGrams: Int?,
    val lastDurationSeconds: Int?,
    val lastDistanceMeters: Int?,
)

data class CreateExerciseRequest(
    @field:NotBlank
    @field:Size(max = EXERCISE_NAME_MAX_LENGTH)
    val name: String,
    val primaryMuscle: MuscleGroup,
    val equipment: Equipment,
    val kind: ExerciseKind,
    @field:Size(max = EXERCISE_DESCRIPTION_MAX_LENGTH)
    val description: String?,
    @field:Size(max = EXERCISE_VIDEO_URL_MAX_LENGTH)
    val videoUrl: String?,
)

data class TrainingSetRequest(
    val exerciseId: UUID,
    val repetitions: Int?,
    val weightGrams: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Int?,
)

data class SaveTrainingLogRequest(
    val slotId: UUID?,
    @field:Size(max = NOTES_MAX_LENGTH)
    val notes: String?,
    @field:Valid
    @field:Size(max = SETS_MAX_COUNT)
    val sets: List<TrainingSetRequest>,
)

data class TrainingSetResponse(
    val id: UUID,
    val exerciseId: UUID,
    val exerciseName: String,
    val kind: ExerciseKind,
    val position: Int,
    val repetitions: Int?,
    val weightGrams: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Int?,
    val isPersonalRecord: Boolean,
)

data class TrainingLogEntryResponse(
    val id: UUID,
    val clientUserId: UUID,
    val entryDate: LocalDate,
    val slotId: UUID?,
    val notes: String?,
    val sets: List<TrainingSetResponse>,
    val totalVolumeGrams: Long,
)

data class DiaryDayResponse(
    val entryDate: LocalDate,
    val volumeGrams: Long,
)

data class ClientDiarySummaryResponse(
    val clientUserId: UUID,
    val displayName: String,
    val linkedAt: Instant,
    val lastEntryDate: LocalDate?,
    val days: List<DiaryDayResponse>,
)

data class AttachExerciseVideoRequest(
    val mediaFileId: UUID,
)

data class ExerciseFilter(
    val search: String?,
    val muscles: List<MuscleGroup>,
    val equipment: List<Equipment>,
    val ownerKind: ExerciseOwnerKind?,
) {

    val isEmpty: Boolean
        get() = search.isNullOrBlank() && muscles.isEmpty() && equipment.isEmpty() && ownerKind == null

    companion object {

        val EMPTY = ExerciseFilter(search = null, muscles = emptyList(), equipment = emptyList(), ownerKind = null)
    }
}
