package app.trainer.backend.traininglog

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.media.MediaFileEntity
import app.trainer.backend.media.MediaFileResponse
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.media.MediaOwnerKind
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val COACH_USER_ID: UUID = UUID.fromString("70000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("70000000-0000-0000-0000-000000000002")
private val OTHER_COACH_ID: UUID = UUID.fromString("70000000-0000-0000-0000-000000000003")
private val EXERCISE_ID: UUID = UUID.fromString("70000000-0000-0000-0000-000000000004")
private val VIDEO_ID: UUID = UUID.fromString("70000000-0000-0000-0000-000000000005")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private const val VIDEO_URL = "https://storage.example/exercises/video.mp4"
private const val VIDEO_SIZE_BYTES = 4_000_000L
private const val WINDOW_HOURS = 12
private const val MORNING_HOUR = 10

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class ExerciseVideoTest {

    private val exerciseRepository = mock(ExerciseRepository::class.java)
    private val entryRepository = mock(TrainingLogEntryRepository::class.java)
    private val setRepository = mock(TrainingLogSetRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val mediaFileService = mock(MediaFileService::class.java)

    private val service = TrainingLogService(
        exerciseRepository = exerciseRepository,
        entryRepository = entryRepository,
        setRepository = setRepository,
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        userRepository = userRepository,
        mediaFileService = mediaFileService,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `attaching a video links the file and remembers it on the exercise`() {
        givenCoach()
        val exercise = exercise(coachId = COACH_ID)
        `when`(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(exercise))
        `when`(mediaFileService.link(anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull()))
            .thenReturn(listOf(videoFile()))
        `when`(mediaFileService.toResponse(anyNonNull())).thenReturn(videoResponse())

        val response = service.attachVideo(
            coachUserId = COACH_USER_ID,
            exerciseId = EXERCISE_ID,
            mediaFileId = VIDEO_ID,
        )

        assertEquals(VIDEO_ID, exercise.videoMediaFileId)
        assertEquals(VIDEO_URL, response.video?.downloadUrl)
        verify(mediaFileService).link(
            mediaFileIds = listOf(VIDEO_ID),
            ownerKind = MediaOwnerKind.EXERCISE,
            ownerId = EXERCISE_ID,
            scopeId = COACH_ID,
            uploaderUserId = COACH_USER_ID,
        )
    }

    @Test
    fun `a video cannot be attached to someone else exercise`() {
        givenCoach()
        `when`(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(exercise(coachId = OTHER_COACH_ID)))

        val failure = assertFailsWith<ResponseStatusException> {
            service.attachVideo(coachUserId = COACH_USER_ID, exerciseId = EXERCISE_ID, mediaFileId = VIDEO_ID)
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode, "чужое упражнение выглядит как несуществующее")
        verify(mediaFileService, never()).link(anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull())
    }

    @Test
    fun `a shared exercise without an owner cannot take a video either`() {
        givenCoach()
        `when`(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(exercise(coachId = null)))

        val failure = assertFailsWith<ResponseStatusException> {
            service.attachVideo(coachUserId = COACH_USER_ID, exerciseId = EXERCISE_ID, mediaFileId = VIDEO_ID)
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    @Test
    fun `detaching forgets the video but keeps the exercise`() {
        givenCoach()
        val exercise = exercise(coachId = COACH_ID).apply { videoMediaFileId = VIDEO_ID }
        `when`(exerciseRepository.findById(EXERCISE_ID)).thenReturn(Optional.of(exercise))

        service.detachVideo(coachUserId = COACH_USER_ID, exerciseId = EXERCISE_ID)

        assertNull(exercise.videoMediaFileId)
        assertEquals("Приседания", exercise.name)
    }

    @Test
    fun `a user who is not a coach cannot upload a video`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.detachVideo(coachUserId = COACH_USER_ID, exerciseId = EXERCISE_ID)
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
    }

    private fun givenCoach() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(
            CoachEntity(
                id = COACH_ID,
                userId = COACH_USER_ID,
                zoneId = "Europe/Moscow",
                cancellationWindowHours = WINDOW_HOURS,
                reminderHour = MORNING_HOUR,
                sessionRemindersEnabled = true,
                diaryRemindersEnabled = true,
                checkInRemindersEnabled = true,
                isOwner = false,
                createdAt = NOW,
            )
        )
    }

    private fun exercise(coachId: UUID?): ExerciseEntity = ExerciseEntity(
        id = EXERCISE_ID,
        ownerKind = ExerciseOwnerKind.COACH,
        ownerId = coachId,
        name = "Приседания",
        primaryMuscle = MuscleGroup.CHEST,
        equipment = Equipment.BARBELL,
        kind = ExerciseKind.STRENGTH,
        description = null,
        videoUrl = null,
        videoMediaFileId = null,
        createdAt = NOW,
        archivedAt = null,
    )

    private fun videoFile(): MediaFileEntity = MediaFileEntity(
        id = VIDEO_ID,
        ownerKind = MediaOwnerKind.EXERCISE,
        ownerId = EXERCISE_ID,
        scopeId = COACH_ID,
        uploadedByUserId = COACH_USER_ID,
        storageKey = "exercises/$COACH_ID/$VIDEO_ID",
        contentType = "video/mp4",
        sizeBytes = VIDEO_SIZE_BYTES,
        originalName = "squat.mp4",
        createdAt = NOW,
        linkedAt = NOW,
    )

    private fun videoResponse(): MediaFileResponse = MediaFileResponse(
        id = VIDEO_ID,
        contentType = "video/mp4",
        sizeBytes = VIDEO_SIZE_BYTES,
        originalName = "squat.mp4",
        downloadUrl = VIDEO_URL,
    )
}
