package app.trainer.backend.traininglog

import java.time.Duration
import java.time.Instant
import java.util.UUID

private const val LONGEST_TRAINING_HOURS = 6L

private val LONGEST_TRAINING: Duration = Duration.ofHours(LONGEST_TRAINING_HOURS)

fun setVolumeOf(set: TrainingLogSetEntity): Long? {
    val repetitions = set.repetitions ?: return null
    val weightGrams = set.weightGrams ?: return null
    val volume = repetitions.toLong() * weightGrams
    return if (volume == 0L) null else volume
}

fun recordSetIdsOf(
    bestVolumeBefore: Map<UUID, Long>,
    setsInDateOrder: List<List<TrainingLogSetEntity>>,
): Set<UUID> {
    val best = bestVolumeBefore.toMutableMap()
    val records = mutableSetOf<UUID>()
    setsInDateOrder.forEach { sets ->
        sets.sortedBy { it.position }.forEach { set ->
            val volume = setVolumeOf(set) ?: return@forEach
            if (volume > (best[set.exerciseId] ?: 0)) {
                records.add(set.id)
                best[set.exerciseId] = volume
            }
        }
    }
    return records
}

fun durationSecondsOf(startedAt: Instant?, finishedAt: Instant?): Long? {
    if (startedAt == null || finishedAt == null) return null
    val duration = Duration.between(startedAt, finishedAt)
    if (duration.isNegative || duration.isZero || duration > LONGEST_TRAINING) return null
    return duration.seconds
}
