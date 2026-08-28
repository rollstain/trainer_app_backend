package app.trainer.backend.traininglog

import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

private val COACH_USER_ID: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000002")
private val CLIENT_USER_ID: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000003")
private val OTHER_CLIENT_ID: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000004")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class ExerciseVisibilityTest {

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
    fun `the coach sees the exercises their clients invented`() {
        givenCoachWithClients(CLIENT_USER_ID, OTHER_CLIENT_ID)
        givenLibrary()

        val visible = service.availableExercises(userId = COACH_USER_ID, limit = null, after = null)

        assertEquals(
            listOf("Общее", "Тренерское", "Анна придумала", "Другой клиент придумал"),
            visible.items.map { it.name },
        )
    }

    @Test
    fun `an archived client stops sharing their exercises`() {
        givenCoachWithClients(CLIENT_USER_ID)
        givenLibrary()

        val visible = service.availableExercises(userId = COACH_USER_ID, limit = null, after = null)

        assertTrue(
            visible.items.none { it.name == "Другой клиент придумал" },
            "отвязанный подопечный больше не делится упражнениями",
        )
    }

    @Test
    fun `a client sees the shared library, their coach and themselves`() {
        givenClientOfCoach()
        givenLibrary()

        val visible = service.availableExercises(userId = CLIENT_USER_ID, limit = null, after = null)

        assertEquals(listOf("Общее", "Тренерское", "Анна придумала"), visible.items.map { it.name })
        assertTrue(
            visible.items.none { it.name == "Другой клиент придумал" },
            "чужие упражнения клиенту не видны",
        )
    }

    @Test
    fun `a client creates an exercise of their own`() {
        `when`(coachRepository.findByUserId(CLIENT_USER_ID)).thenReturn(null)
        `when`(exerciseRepository.save(anyNonNull<ExerciseEntity>()))
            .thenAnswer { it.arguments.first() as ExerciseEntity }
        `when`(userRepository.findById(CLIENT_USER_ID)).thenReturn(Optional.of(client()))

        val created = service.createExercise(userId = CLIENT_USER_ID, request = request())

        assertEquals(ExerciseOwnerKind.CLIENT, created.ownerKind)
        assertEquals("Анна", created.ownerDisplayName, "тренер увидит, чьё это упражнение")
        assertEquals(MuscleGroup.QUADRICEPS, created.primaryMuscle)
        assertEquals(Equipment.BODYWEIGHT, created.equipment)
    }

    @Test
    fun `an exercise made by a coach belongs to the coach`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(exerciseRepository.save(anyNonNull<ExerciseEntity>()))
            .thenAnswer { it.arguments.first() as ExerciseEntity }

        val created = service.createExercise(userId = COACH_USER_ID, request = request())

        assertEquals(ExerciseOwnerKind.COACH, created.ownerKind)
        assertNull(created.ownerDisplayName, "у своих упражнений автор не нужен")
    }

    private fun givenLibrary() {
        val library = listOf(
            exercise(name = "Общее", ownerKind = ExerciseOwnerKind.SHARED, ownerId = null),
            exercise(name = "Тренерское", ownerKind = ExerciseOwnerKind.COACH, ownerId = COACH_ID),
            exercise(name = "Анна придумала", ownerKind = ExerciseOwnerKind.CLIENT, ownerId = CLIENT_USER_ID),
            exercise(
                name = "Другой клиент придумал",
                ownerKind = ExerciseOwnerKind.CLIENT,
                ownerId = OTHER_CLIENT_ID,
            ),
        )
        `when`(exerciseRepository.findAvailable(anyNonNull())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val ownerIds = (invocation.arguments.first() as Array<UUID>).toSet()
            library.filter { it.ownerKind == ExerciseOwnerKind.SHARED || ownerIds.contains(it.ownerId) }
        }
        `when`(userRepository.findById(anyNonNull())).thenReturn(Optional.of(client()))
    }

    private fun exercise(name: String, ownerKind: ExerciseOwnerKind, ownerId: UUID?): ExerciseEntity =
        ExerciseEntity(
            id = UUID.randomUUID(),
            ownerKind = ownerKind,
            ownerId = ownerId,
            name = name,
            primaryMuscle = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            kind = ExerciseKind.STRENGTH,
            description = null,
            videoUrl = null,
            videoMediaFileId = null,
            createdAt = NOW,
            archivedAt = null,
        )

    private fun request() = CreateExerciseRequest(
        name = "Выпады на месте",
        primaryMuscle = MuscleGroup.QUADRICEPS,
        equipment = Equipment.BODYWEIGHT,
        kind = ExerciseKind.BODYWEIGHT,
        description = null,
        videoUrl = null,
    )

    private fun givenCoachWithClients(vararg clientIds: UUID) {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(coachClientRepository.findByCoachIdAndStatus(COACH_ID, CoachClientStatus.ACTIVE))
            .thenReturn(clientIds.map(::link))
    }

    private fun givenClientOfCoach() {
        `when`(coachRepository.findByUserId(CLIENT_USER_ID)).thenReturn(null)
        `when`(coachClientRepository.findByUserId(CLIENT_USER_ID)).thenReturn(listOf(link(CLIENT_USER_ID)))
    }

    private fun link(userId: UUID): CoachClientEntity = CoachClientEntity(
        id = UUID.randomUUID(),
        coachId = COACH_ID,
        userId = userId,
        status = CoachClientStatus.ACTIVE,
        createdAt = NOW,
    )

    private fun client(): UserEntity = UserEntity(
        id = CLIENT_USER_ID,
        displayName = "Анна",
        phone = null,
        email = null,
        createdAt = NOW,
    )

    private fun coach(): CoachEntity = CoachEntity(
        id = COACH_ID,
        userId = COACH_USER_ID,
        zoneId = "Europe/Moscow",
        cancellationWindowHours = CANCELLATION_WINDOW_HOURS,
        reminderHour = REMINDER_HOUR,
        sessionRemindersEnabled = true,
        diaryRemindersEnabled = true,
        checkInRemindersEnabled = true,
        createdAt = NOW,
    )
}
