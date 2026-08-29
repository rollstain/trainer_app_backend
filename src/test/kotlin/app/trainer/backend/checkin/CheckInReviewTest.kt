package app.trainer.backend.checkin

import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.media.MediaFileService
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
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val COACH_USER_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000002")
private val CLIENT_USER_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000003")
private val CHECK_IN_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000004")
private val OTHER_CLIENT_ID: UUID = UUID.fromString("20000000-0000-0000-0000-000000000005")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private val CHECK_IN_DATE: LocalDate = LocalDate.of(2026, 3, 1)
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10
private const val AWAITING_LIMIT = 20

class CheckInReviewTest {

    private val checkInRepository = mock(CheckInRepository::class.java)
    private val mediaFileService = mock(MediaFileService::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)

    private val userRepository = mock(UserRepository::class.java)

    private val service = CheckInService(
        checkInRepository = checkInRepository,
        mediaFileService = mediaFileService,
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        userRepository = userRepository,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `check-ins waiting for an answer come back with the client name`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(checkInRepository.findAwaitingReview(COACH_ID, AWAITING_LIMIT))
            .thenReturn(listOf(checkIn()))
        `when`(userRepository.findAllById(listOf(CLIENT_USER_ID))).thenReturn(listOf(client()))

        val awaiting = service.awaitingReview(coachUserId = COACH_USER_ID)

        assertEquals(1, awaiting.size)
        assertEquals("Анна", awaiting.single().clientDisplayName)
        assertEquals(CHECK_IN_DATE, awaiting.single().checkInDate)
    }

    @Test
    fun `a user who is not a coach has nothing waiting`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.awaitingReview(coachUserId = COACH_USER_ID)
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
        createdAt = NOW,
    )

    private fun checkIn(clientUserId: UUID = CLIENT_USER_ID): CheckInEntity = CheckInEntity(
        id = CHECK_IN_ID,
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
