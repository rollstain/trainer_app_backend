package app.trainer.backend.traininglog

import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val FEED_COACH_USER_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000001")
private val FEED_COACH_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000002")
private val ANNA: UUID = UUID.fromString("80000000-0000-0000-0000-000000000003")
private val SQUAT_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000004")
private val ENTRY_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000005")
private val OLDER_ENTRY_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000006")
private val NOW: Instant = Instant.parse("2026-09-30T09:00:00Z")
private val TODAY: LocalDate = LocalDate.of(2026, 9, 30)
private const val WINDOW_HOURS = 12
private const val MORNING_HOUR = 10
private const val PAGE_SIZE = 1
private const val REPETITIONS = 5
private const val EIGHTY_KILOGRAMS = 80_000
private const val PREVIOUS_BEST_GRAMS = 390_000L
private const val FORTY_EIGHT_MINUTES_IN_SECONDS = 2_880L
private const val UNSEEN_TRAININGS = 3L

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class TrainingFeedServiceTest {

    private val coachRepository = mock(CoachRepository::class.java)
    private val entryRepository = mock(TrainingLogEntryRepository::class.java)
    private val setRepository = mock(TrainingLogSetRepository::class.java)
    private val exerciseRepository = mock(ExerciseRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)

    private val service = TrainingFeedService(
        coachRepository = coachRepository,
        entryRepository = entryRepository,
        setRepository = setRepository,
        exerciseRepository = exerciseRepository,
        userRepository = userRepository,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `the feed shows a fresh workout with its size, duration and records`() {
        givenCoach()
        val entry = entry(id = ENTRY_ID, updatedAt = NOW)
        `when`(entryRepository.findFeedPage(FEED_COACH_ID, true, null, null, PAGE_SIZE + 1))
            .thenReturn(listOf(entry, entry(id = OLDER_ENTRY_ID, updatedAt = NOW.minusSeconds(1))))
        givenSets()

        val page = service.feed(coachUserId = FEED_COACH_USER_ID, unseenOnly = true, limit = PAGE_SIZE, after = null)

        val item = page.items.single()
        assertEquals("Анна", item.clientDisplayName)
        assertEquals(1, item.exercisesCount)
        assertEquals(2, item.setsCount)
        assertEquals(FORTY_EIGHT_MINUTES_IN_SECONDS, item.durationSeconds)
        assertEquals(listOf("Приседания"), item.personalRecords.map { it.exerciseName })
        assertEquals(false, item.isSeen)
        val cursor = assertNotNull(decodeCursor(page.nextCursor), "запись сверх страницы говорит, что есть ещё")
        assertEquals(ENTRY_ID, cursor.id)
    }

    @Test
    fun `an empty feed does not load sets or names`() {
        givenCoach()
        `when`(entryRepository.findFeedPage(FEED_COACH_ID, false, null, null, PAGE_SIZE + 1)).thenReturn(emptyList())

        val page = service.feed(coachUserId = FEED_COACH_USER_ID, unseenOnly = false, limit = PAGE_SIZE, after = null)

        assertTrue(page.items.isEmpty())
        assertNull(page.nextCursor)
        verify(setRepository, never()).findByEntryIdInOrderByPositionAsc(anyNonNull())
    }

    @Test
    fun `a workout of somebody else's client cannot be marked as seen`() {
        givenCoach()
        `when`(entryRepository.isEntryOfActiveClient(FEED_COACH_ID, ENTRY_ID)).thenReturn(false)

        val failure = assertFailsWith<ResponseStatusException> {
            service.markSeen(coachUserId = FEED_COACH_USER_ID, entryId = ENTRY_ID)
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
        verify(entryRepository, never()).markSeen(anyNonNull(), anyNonNull(), anyNonNull())
    }

    @Test
    fun `marking a workout as seen stamps the current moment`() {
        givenCoach()
        `when`(entryRepository.isEntryOfActiveClient(FEED_COACH_ID, ENTRY_ID)).thenReturn(true)

        service.markSeen(coachUserId = FEED_COACH_USER_ID, entryId = ENTRY_ID)

        verify(entryRepository).markSeen(FEED_COACH_ID, ENTRY_ID, NOW)
    }

    @Test
    fun `the queue counts unseen workouts of the coach`() {
        givenCoach()
        `when`(entryRepository.countUnseen(FEED_COACH_ID)).thenReturn(UNSEEN_TRAININGS)

        assertEquals(UNSEEN_TRAININGS.toInt(), service.unseenCount(FEED_COACH_USER_ID))
    }

    private fun givenCoach() {
        `when`(coachRepository.findByUserId(FEED_COACH_USER_ID)).thenReturn(
            CoachEntity(
                id = FEED_COACH_ID,
                userId = FEED_COACH_USER_ID,
                zoneId = "Europe/Moscow",
                cancellationWindowHours = WINDOW_HOURS,
                reminderHour = MORNING_HOUR,
                sessionRemindersEnabled = true,
                diaryRemindersEnabled = true,
                checkInRemindersEnabled = true,
                createdAt = NOW,
            )
        )
    }

    private fun givenSets() {
        val sets = listOf(set(position = 0), set(position = 1))
        `when`(setRepository.findByEntryIdInOrderByPositionAsc(listOf(ENTRY_ID))).thenReturn(sets)
        `when`(setRepository.bestVolumePerExerciseBefore(ANNA, TODAY)).thenReturn(
            listOf(
                object : ExerciseBestVolume {
                    override fun getExerciseId(): UUID = SQUAT_ID

                    override fun getBestVolume(): Long = PREVIOUS_BEST_GRAMS
                }
            )
        )
        `when`(exerciseRepository.findAllById(listOf(SQUAT_ID))).thenReturn(listOf(squat()))
        `when`(userRepository.findAllById(listOf(ANNA))).thenReturn(
            listOf(
                UserEntity(
                    id = ANNA,
                    displayName = "Анна",
                    phone = null,
                    email = null,
                    login = null,
                    isOwner = false,
                    createdAt = NOW,
                )
            )
        )
        `when`(entryRepository.findSeenEntryIds(FEED_COACH_ID, listOf(ENTRY_ID))).thenReturn(emptyList())
    }

    private fun entry(id: UUID, updatedAt: Instant): TrainingLogEntryEntity = TrainingLogEntryEntity(
        id = id,
        clientUserId = ANNA,
        entryDate = TODAY,
        slotId = null,
        notes = null,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        startedAt = NOW.minusSeconds(FORTY_EIGHT_MINUTES_IN_SECONDS),
        finishedAt = NOW,
    )

    private fun set(position: Int): TrainingLogSetEntity = TrainingLogSetEntity(
        id = UUID.randomUUID(),
        entryId = ENTRY_ID,
        exerciseId = SQUAT_ID,
        position = position,
        repetitions = REPETITIONS,
        weightGrams = EIGHTY_KILOGRAMS,
        durationSeconds = null,
        distanceMeters = null,
    )

    private fun squat(): ExerciseEntity = ExerciseEntity(
        id = SQUAT_ID,
        ownerKind = ExerciseOwnerKind.SHARED,
        ownerId = null,
        name = "Приседания",
        primaryMuscle = MuscleGroup.QUADRICEPS,
        equipment = Equipment.BARBELL,
        kind = ExerciseKind.STRENGTH,
        description = null,
        videoUrl = null,
        videoMediaFileId = null,
        createdAt = NOW,
        archivedAt = null,
    )
}
