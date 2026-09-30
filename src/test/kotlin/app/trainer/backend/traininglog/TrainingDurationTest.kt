package app.trainer.backend.traininglog

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

private val CLIENT_USER_ID: UUID = UUID.fromString("90000000-0000-0000-0000-000000000001")
private val NOW: Instant = Instant.parse("2026-09-30T09:00:00Z")
private val TODAY: LocalDate = LocalDate.of(2026, 9, 30)
private val STARTED: Instant = Instant.parse("2026-09-30T08:10:00Z")
private val FINISHED: Instant = Instant.parse("2026-09-30T08:58:00Z")
private const val FORTY_EIGHT_MINUTES_IN_SECONDS = 2_880L

class TrainingDurationTest {

    private val entryRepository = mock(TrainingLogEntryRepository::class.java)

    private val service = TrainingLogService(
        exerciseRepository = mock(ExerciseRepository::class.java),
        entryRepository = entryRepository,
        setRepository = mock(TrainingLogSetRepository::class.java),
        coachRepository = mock(CoachRepository::class.java),
        coachClientRepository = mock(CoachClientRepository::class.java),
        userRepository = mock(UserRepository::class.java),
        mediaFileService = mock(MediaFileService::class.java),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `the start and the finish of a workout are kept and give its duration`() {
        val entry = existingEntry()

        val saved = service.saveEntry(
            clientUserId = CLIENT_USER_ID,
            entryDate = TODAY,
            request = request(startedAt = STARTED, finishedAt = FINISHED),
        )

        assertEquals(STARTED, entry.startedAt)
        assertEquals(FINISHED, entry.finishedAt)
        assertEquals(STARTED, saved.startedAt)
        assertEquals(FINISHED, saved.finishedAt)
        assertEquals(FORTY_EIGHT_MINUTES_IN_SECONDS, saved.durationSeconds)
    }

    @Test
    fun `a save without times keeps the ones already recorded`() {
        val entry = existingEntry(startedAt = STARTED, finishedAt = FINISHED)

        val saved = service.saveEntry(
            clientUserId = CLIENT_USER_ID,
            entryDate = TODAY,
            request = request(startedAt = null, finishedAt = null),
        )

        assertEquals(STARTED, entry.startedAt)
        assertEquals(FORTY_EIGHT_MINUTES_IN_SECONDS, saved.durationSeconds)
    }

    private fun existingEntry(startedAt: Instant? = null, finishedAt: Instant? = null): TrainingLogEntryEntity {
        val entry = TrainingLogEntryEntity(
            id = UUID.fromString("90000000-0000-0000-0000-000000000002"),
            clientUserId = CLIENT_USER_ID,
            entryDate = TODAY,
            slotId = null,
            notes = null,
            createdAt = NOW,
            updatedAt = NOW,
            startedAt = startedAt,
            finishedAt = finishedAt,
        )
        `when`(entryRepository.findByClientUserIdAndEntryDate(CLIENT_USER_ID, TODAY)).thenReturn(entry)
        return entry
    }

    private fun request(startedAt: Instant?, finishedAt: Instant?): SaveTrainingLogRequest = SaveTrainingLogRequest(
        slotId = null,
        notes = null,
        startedAt = startedAt,
        finishedAt = finishedAt,
        sets = emptyList(),
    )
}
