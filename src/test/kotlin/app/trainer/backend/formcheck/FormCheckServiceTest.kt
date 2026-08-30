package app.trainer.backend.formcheck

import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.config.PageCursor
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.config.encodeCursor
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.media.MediaOwnerKind
import app.trainer.backend.traininglog.ExerciseRepository
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val CLIENT_USER_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000001")
private val COACH_USER_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000002")
private val COACH_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000003")
private val OTHER_COACH_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000004")
private val VIDEO_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000005")
private val FORM_CHECK_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000006")
private val OTHER_FORM_CHECK_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000007")
private val THIRD_FORM_CHECK_ID: UUID = UUID.fromString("80000000-0000-0000-0000-000000000008")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private const val PAGE_SIZE = 2
private const val PAGE_SIZE_WITH_PROBE = PAGE_SIZE + 1
private const val WINDOW_HOURS = 12
private const val MORNING_HOUR = 10

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class FormCheckServiceTest {

    private val formCheckRepository = mock(FormCheckRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)
    private val exerciseRepository = mock(ExerciseRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val mediaFileService = mock(MediaFileService::class.java)

    private val service = FormCheckService(
        formCheckRepository = formCheckRepository,
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        exerciseRepository = exerciseRepository,
        userRepository = userRepository,
        mediaFileService = mediaFileService,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `a submitted video goes to the coach of that client`() {
        givenClientWithCoach()

        val created = service.create(
            clientUserId = CLIENT_USER_ID,
            request = CreateFormCheckRequest(mediaFileId = VIDEO_ID, exerciseId = null, note = "  Приседания  "),
        )

        assertEquals(CLIENT_USER_ID, created.clientUserId)
        assertEquals("Приседания", created.note, "лишние пробелы обрезаются")
        assertTrue(!created.isReviewed, "новый разбор ждёт ответа")
        verify(mediaFileService).link(
            mediaFileIds = listOf(VIDEO_ID),
            ownerKind = MediaOwnerKind.FORM_CHECK,
            ownerId = created.id,
            scopeId = COACH_ID,
            uploaderUserId = CLIENT_USER_ID,
        )
    }

    @Test
    fun `a client without a coach has nowhere to send a video`() {
        `when`(coachClientRepository.findByUserId(CLIENT_USER_ID)).thenReturn(emptyList())

        val failure = assertFailsWith<ResponseStatusException> {
            service.create(
                clientUserId = CLIENT_USER_ID,
                request = CreateFormCheckRequest(mediaFileId = VIDEO_ID, exerciseId = null, note = null),
            )
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
        verify(mediaFileService, never()).link(anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull())
    }

    @Test
    fun `an archived link does not count as having a coach`() {
        `when`(coachClientRepository.findByUserId(CLIENT_USER_ID))
            .thenReturn(listOf(link(status = CoachClientStatus.ARCHIVED)))

        val failure = assertFailsWith<ResponseStatusException> {
            service.create(
                clientUserId = CLIENT_USER_ID,
                request = CreateFormCheckRequest(mediaFileId = VIDEO_ID, exerciseId = null, note = null),
            )
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
    }

    @Test
    fun `a review marks the check answered and remembers the comment`() {
        givenCoach()
        val formCheck = formCheck()
        `when`(formCheckRepository.findById(FORM_CHECK_ID)).thenReturn(Optional.of(formCheck))

        val reviewed = service.review(
            coachUserId = COACH_USER_ID,
            formCheckId = FORM_CHECK_ID,
            request = ReviewFormCheckRequest(comment = "  Колени внутрь, снизь вес  "),
        )

        assertEquals("Колени внутрь, снизь вес", formCheck.coachComment)
        assertEquals(NOW, formCheck.reviewedAt)
        assertEquals(COACH_ID, formCheck.reviewedByCoachId)
        assertTrue(reviewed.isReviewed)
    }

    @Test
    fun `an empty comment still counts as answered`() {
        givenCoach()
        val formCheck = formCheck()
        `when`(formCheckRepository.findById(FORM_CHECK_ID)).thenReturn(Optional.of(formCheck))

        val reviewed = service.review(
            coachUserId = COACH_USER_ID,
            formCheckId = FORM_CHECK_ID,
            request = ReviewFormCheckRequest(comment = "   "),
        )

        assertNull(formCheck.coachComment)
        assertTrue(reviewed.isReviewed, "тренер посмотрел и согласился — разбор уходит из очереди")
    }

    @Test
    fun `someone else's form check cannot be reviewed`() {
        givenCoach()
        `when`(formCheckRepository.findById(FORM_CHECK_ID))
            .thenReturn(Optional.of(formCheck(coachId = OTHER_COACH_ID)))

        val failure = assertFailsWith<ResponseStatusException> {
            service.review(
                coachUserId = COACH_USER_ID,
                formCheckId = FORM_CHECK_ID,
                request = ReviewFormCheckRequest(comment = "нет"),
            )
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    @Test
    fun `the awaiting queue reads names and exercises in one go`() {
        givenCoach()
        `when`(formCheckRepository.findAwaitingPage(COACH_ID, null, null, PAGE_SIZE_WITH_PROBE))
            .thenReturn(listOf(formCheck(), formCheck(id = OTHER_FORM_CHECK_ID)))
        `when`(userRepository.findAllById(anyNonNull())).thenReturn(listOf(client()))
        `when`(exerciseRepository.findAllById(anyNonNull())).thenReturn(emptyList())

        val awaiting = service.awaitingReview(coachUserId = COACH_USER_ID, limit = PAGE_SIZE, after = null)

        assertEquals(2, awaiting.items.size)
        assertEquals(listOf("Анна", "Анна"), awaiting.items.map { it.clientDisplayName })
        assertNull(awaiting.nextCursor, "очередь уместилась целиком — продолжения нет")
        verify(userRepository, times(1)).findAllById(anyNonNull())
        verify(userRepository, never()).findById(anyNonNull())
    }

    @Test
    fun `a full page of the awaiting queue points at the rest with a cursor`() {
        givenCoach()
        `when`(formCheckRepository.findAwaitingPage(COACH_ID, null, null, PAGE_SIZE_WITH_PROBE))
            .thenReturn(
                listOf(formCheck(), formCheck(id = OTHER_FORM_CHECK_ID), formCheck(id = THIRD_FORM_CHECK_ID))
            )
        `when`(userRepository.findAllById(anyNonNull())).thenReturn(listOf(client()))
        `when`(exerciseRepository.findAllById(anyNonNull())).thenReturn(emptyList())

        val awaiting = service.awaitingReview(coachUserId = COACH_USER_ID, limit = PAGE_SIZE, after = null)

        assertEquals(PAGE_SIZE, awaiting.items.size)
        assertEquals(
            PageCursor(sortKey = NOW.toString(), id = OTHER_FORM_CHECK_ID),
            decodeCursor(awaiting.nextCursor),
        )
    }

    @Test
    fun `the next page of own history starts after the cursor`() {
        `when`(
            formCheckRepository.findClientPage(CLIENT_USER_ID, NOW.toString(), FORM_CHECK_ID, PAGE_SIZE_WITH_PROBE)
        ).thenReturn(listOf(formCheck(id = OTHER_FORM_CHECK_ID)))
        `when`(userRepository.findAllById(anyNonNull())).thenReturn(listOf(client()))
        `when`(exerciseRepository.findAllById(anyNonNull())).thenReturn(emptyList())

        val history = service.ownFormChecks(
            clientUserId = CLIENT_USER_ID,
            limit = PAGE_SIZE,
            after = encodeCursor(PageCursor(sortKey = NOW.toString(), id = FORM_CHECK_ID)),
        )

        assertEquals(listOf(OTHER_FORM_CHECK_ID), history.items.map { it.id })
        assertNull(history.nextCursor)
    }

    private fun givenCoach() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
    }

    private fun givenClientWithCoach() {
        `when`(coachClientRepository.findByUserId(CLIENT_USER_ID))
            .thenReturn(listOf(link(status = CoachClientStatus.ACTIVE)))
        `when`(coachRepository.findById(COACH_ID)).thenReturn(Optional.of(coach()))
        `when`(userRepository.findById(CLIENT_USER_ID)).thenReturn(Optional.of(client()))
    }

    private fun coach(): CoachEntity = CoachEntity(
        id = COACH_ID,
        userId = COACH_USER_ID,
        zoneId = "Europe/Moscow",
        cancellationWindowHours = WINDOW_HOURS,
        reminderHour = MORNING_HOUR,
        sessionRemindersEnabled = true,
        diaryRemindersEnabled = true,
        checkInRemindersEnabled = true,
        createdAt = NOW,
    )

    private fun link(status: CoachClientStatus): CoachClientEntity = CoachClientEntity(
        id = UUID.randomUUID(),
        coachId = COACH_ID,
        userId = CLIENT_USER_ID,
        status = status,
        createdAt = NOW,
    )

    private fun client(): UserEntity = UserEntity(
        id = CLIENT_USER_ID,
        displayName = "Анна",
        phone = null,
        email = null,
        login = null,
        isOwner = false,
        createdAt = NOW,
    )

    private fun formCheck(coachId: UUID = COACH_ID, id: UUID = FORM_CHECK_ID): FormCheckEntity = FormCheckEntity(
        id = id,
        clientUserId = CLIENT_USER_ID,
        coachId = coachId,
        exerciseId = null,
        mediaFileId = VIDEO_ID,
        note = null,
        coachComment = null,
        reviewedAt = null,
        reviewedByCoachId = null,
        createdAt = NOW,
    )
}
