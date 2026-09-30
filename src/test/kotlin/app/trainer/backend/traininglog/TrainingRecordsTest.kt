package app.trainer.backend.traininglog

import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

private val SQUAT: UUID = UUID.fromString("70000000-0000-0000-0000-000000000001")
private val START: Instant = Instant.parse("2026-09-30T06:00:00Z")
private const val FORTY_EIGHT_MINUTES_IN_SECONDS = 2_880L
private const val SEVEN_HOURS_IN_SECONDS = 25_200L
private const val SQUAT_REPETITIONS = 5
private const val EIGHTY_KILOGRAMS = 80_000
private const val SEVENTY_FIVE_KILOGRAMS = 75_000
private const val BEST_BEFORE_GRAMS = 390_000L

class TrainingRecordsTest {

    @Test
    fun `a finished workout knows how long it took`() {
        assertEquals(
            FORTY_EIGHT_MINUTES_IN_SECONDS,
            durationSecondsOf(startedAt = START, finishedAt = START.plusSeconds(FORTY_EIGHT_MINUTES_IN_SECONDS)),
        )
    }

    @Test
    fun `without both ends there is no duration`() {
        assertNull(durationSecondsOf(startedAt = START, finishedAt = null))
        assertNull(durationSecondsOf(startedAt = null, finishedAt = START))
    }

    @Test
    fun `an end before the start or a forgotten finish is not a duration`() {
        assertNull(durationSecondsOf(startedAt = START, finishedAt = START.minusSeconds(1)))
        assertNull(durationSecondsOf(startedAt = START, finishedAt = START))
        assertNull(durationSecondsOf(startedAt = START, finishedAt = START.plusSeconds(SEVEN_HOURS_IN_SECONDS)))
    }

    @Test
    fun `only a set heavier than every earlier one is a record, and only the first of equals`() {
        val warmUp = set(position = 0, weightGrams = SEVENTY_FIVE_KILOGRAMS)
        val heavy = set(position = 1, weightGrams = EIGHTY_KILOGRAMS)
        val sameAgain = set(position = 2, weightGrams = EIGHTY_KILOGRAMS)

        val records = recordSetIdsOf(
            bestVolumeBefore = mapOf(SQUAT to BEST_BEFORE_GRAMS),
            setsInDateOrder = listOf(listOf(sameAgain, warmUp, heavy)),
        )

        assertEquals(setOf(heavy.id), records)
    }

    private fun set(position: Int, weightGrams: Int): TrainingLogSetEntity = TrainingLogSetEntity(
        id = UUID.randomUUID(),
        entryId = UUID.fromString("70000000-0000-0000-0000-000000000002"),
        exerciseId = SQUAT,
        position = position,
        repetitions = SQUAT_REPETITIONS,
        weightGrams = weightGrams,
        durationSeconds = null,
        distanceMeters = null,
    )
}
