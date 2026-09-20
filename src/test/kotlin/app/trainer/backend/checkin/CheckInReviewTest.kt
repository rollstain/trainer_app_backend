package app.trainer.backend.checkin

import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.config.PageCursor
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val COACH_USER_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000002")
private val CLIENT_USER_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000003")
private val CHECK_IN_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000004")
private val OTHER_CLIENT_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000005")
private val OTHER_CHECK_IN_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000006")
private val THIRD_CHECK_IN_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000007")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private val CHECK_IN_DATE: LocalDate = LocalDate.of(2026, 3, 1)
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10
private const val PAGE_SIZE = 2
private const val PAGE_SIZE_WITH_PROBE = PAGE_SIZE + 1
private const val AWAITING_BEYOND_A_PAGE = 25L

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
private fun <T> capturedBy(captor: ArgumentCaptor<*>): T = captor.capture() as T

class CheckInReviewTest {

    private val checkInRepository = mock(CheckInRepository::class.java)
    private val mediaFileService = mock(MediaFileService::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)

    private val userRepository = mock(UserRepository::class.java)
    private val pushSender = mock(PushSender::class.java)

    private val service = CheckInService(
        checkInRepository = checkInRepository,
        mediaFileService = mediaFileService,
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        userRepository = userRepository,
        pushSender = pushSender,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `a check-in sent for the first time reaches the coach`() {
        givenClientOf(coach())
        `when`(checkInRepository.findByClientUserIdAndCheckInDate(CLIENT_USER_ID, CHECK_IN_DATE)).thenReturn(null)
        `when`(checkInRepository.save(anyNonNull<CheckInEntity>())).thenAnswer { it.arguments.first() }
        val message = ArgumentCaptor.forClass(PushMessage::class.java)

        service.save(clientUserId = CLIENT_USER_ID, checkInDate = CHECK_IN_DATE, request = emptyCheckIn())

        verify(pushSender).send(anyNonNull(), capturedBy(message))
        assertEquals(PushText.NEW_CHECK_IN, message.value.text)
        assertEquals(listOf("Анна", "01.03"), message.value.args)
    }

    @Test
    fun `saving the same check-in again says nothing to the coach`() {
        givenClientOf(coach())
        `when`(checkInRepository.findByClientUserIdAndCheckInDate(CLIENT_USER_ID, CHECK_IN_DATE))
            .thenReturn(checkIn())

        service.save(clientUserId = CLIENT_USER_ID, checkInDate = CHECK_IN_DATE, request = emptyCheckIn())

        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `check-ins waiting for an answer come back with the client name`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(checkInRepository.findAwaitingPage(COACH_ID, null, null, PAGE_SIZE_WITH_PROBE))
            .thenReturn(listOf(checkIn()))
        `when`(userRepository.findAllById(listOf(CLIENT_USER_ID))).thenReturn(listOf(client()))

        val awaiting = service.awaitingReview(coachUserId = COACH_USER_ID, limit = PAGE_SIZE, after = null)

        assertEquals(1, awaiting.items.size)
        assertEquals("Анна", awaiting.items.single().clientDisplayName)
        assertEquals(CHECK_IN_DATE, awaiting.items.single().checkInDate)
        assertNull(awaiting.nextCursor, "очередь уместилась целиком — продолжения нет")
    }

    @Test
    fun `a full page of check-ins comes back with a cursor to the rest`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(checkInRepository.findAwaitingPage(COACH_ID, null, null, PAGE_SIZE_WITH_PROBE))
            .thenReturn(listOf(checkIn(), checkIn(id = OTHER_CHECK_IN_ID), checkIn(id = THIRD_CHECK_IN_ID)))
        `when`(userRepository.findAllById(listOf(CLIENT_USER_ID))).thenReturn(listOf(client()))

        val awaiting = service.awaitingReview(coachUserId = COACH_USER_ID, limit = PAGE_SIZE, after = null)

        assertEquals(PAGE_SIZE, awaiting.items.size)
        assertEquals(
            PageCursor(sortKey = CHECK_IN_DATE.toString(), id = OTHER_CHECK_IN_ID),
            decodeCursor(awaiting.nextCursor),
        )
    }

    @Test
    fun `the coach sees how many check-ins wait in all, not just on the first page`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(checkInRepository.countAwaiting(COACH_ID)).thenReturn(AWAITING_BEYOND_A_PAGE)

        assertEquals(AWAITING_BEYOND_A_PAGE.toInt(), service.awaitingCount(COACH_USER_ID))
    }

    @Test
    fun `a user who is not a coach has nothing waiting`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.awaitingReview(coachUserId = COACH_USER_ID, limit = PAGE_SIZE, after = null)
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
    }

    @Test
    fun `a review stores the comment and marks the check-in answered`() {
        givenCoachWithClient()
        val checkIn = checkIn()
        `when`(checkInRepository.findById(CHECK_IN_ID)).thenReturn(Optional.of(checkIn))

        val reviewed = service.review(
            coachUserId = COACH_USER_ID,
            clientUserId = CLIENT_USER_ID,
            checkInId = CHECK_IN_ID,
            request = ReviewCheckInRequest(comment = "  Хорошая неделя, добавим вес  "),
        )

        assertEquals("Хорошая неделя, добавим вес", checkIn.coachComment)
        assertEquals(NOW, checkIn.reviewedAt)
        assertEquals(COACH_ID, checkIn.reviewedByCoachId)
        assertTrue(reviewed.isReviewed)
        assertEquals("Хорошая неделя, добавим вес", reviewed.coachComment)
    }

    @Test
    fun `an empty comment still counts as answered`() {
        givenCoachWithClient()
        val checkIn = checkIn()
        `when`(checkInRepository.findById(CHECK_IN_ID)).thenReturn(Optional.of(checkIn))

        val reviewed = service.review(
            coachUserId = COACH_USER_ID,
            clientUserId = CLIENT_USER_ID,
            checkInId = CHECK_IN_ID,
            request = ReviewCheckInRequest(comment = "   "),
        )

        assertNull(checkIn.coachComment)
        assertTrue(reviewed.isReviewed)
    }

    @Test
    fun `a check-in of another client cannot be reviewed`() {
        givenCoachWithClient()
        `when`(checkInRepository.findById(CHECK_IN_ID))
            .thenReturn(Optional.of(checkIn(clientUserId = OTHER_CLIENT_ID)))

        val failure = assertFailsWith<ResponseStatusException> {
            service.review(
                coachUserId = COACH_USER_ID,
                clientUserId = CLIENT_USER_ID,
                checkInId = CHECK_IN_ID,
                request = ReviewCheckInRequest(comment = "нет"),
            )
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    @Test
    fun `a coach without this client cannot review`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.review(
                coachUserId = COACH_USER_ID,
                clientUserId = CLIENT_USER_ID,
                checkInId = CHECK_IN_ID,
                request = ReviewCheckInRequest(comment = "нет"),
            )
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
    }

    private fun givenCoachWithClient() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(
            CoachClientEntity(
                id = UUID.fromString("20000000-0000-0000-0000-000000000006"),
                coachId = COACH_ID,
                userId = CLIENT_USER_ID,
                status = CoachClientStatus.ACTIVE,
                createdAt = NOW,
            )
        )
    }

    private fun givenClientOf(coach: CoachEntity) {
        `when`(coachClientRepository.findByUserId(CLIENT_USER_ID)).thenReturn(
            listOf(
                CoachClientEntity(
                    id = UUID.randomUUID(),
                    coachId = coach.id,
                    userId = CLIENT_USER_ID,
                    status = CoachClientStatus.ACTIVE,
                    createdAt = NOW,
                )
            )
        )
        `when`(coachRepository.findById(coach.id)).thenReturn(Optional.of(coach))
        `when`(userRepository.findById(CLIENT_USER_ID)).thenReturn(Optional.of(client()))
    }

    private fun emptyCheckIn(): SaveCheckInRequest = SaveCheckInRequest(
        weightGrams = null,
        waistMillimeters = null,
        chestMillimeters = null,
        hipsMillimeters = null,
        wellbeing = null,
        sleepQuality = null,
        adherence = null,
        notes = null,
        photoIds = emptyList(),
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

    private fun client(): app.trainer.backend.user.UserEntity = app.trainer.backend.user.UserEntity(
        id = CLIENT_USER_ID,
        displayName = "Анна",
        phone = null,
        email = null,
        login = null,
        isOwner = false,
        createdAt = NOW,
    )

    private fun checkIn(clientUserId: UUID = CLIENT_USER_ID, id: UUID = CHECK_IN_ID): CheckInEntity = CheckInEntity(
        id = id,
        clientUserId = clientUserId,
        checkInDate = CHECK_IN_DATE,
        weightGrams = null,
        waistMillimeters = null,
        chestMillimeters = null,
        hipsMillimeters = null,
        wellbeing = null,
        sleepQuality = null,
        notes = null,
        adherence = null,
        coachComment = null,
        reviewedAt = null,
        reviewedByCoachId = null,
        createdAt = NOW,
        updatedAt = NOW,
    )
}
