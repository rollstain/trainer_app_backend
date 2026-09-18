package app.trainer.backend.schedule

import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
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
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val COACH_USER_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000002")
private val SLOT_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000003")
private val CLIENT: UUID = UUID.fromString("91000000-0000-0000-0000-000000000004")
private val STRANGER: UUID = UUID.fromString("91000000-0000-0000-0000-000000000005")
private val REQUEST_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000006")
private val FREE_SLOT_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000007")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private val AHEAD_OF_WINDOW: Instant = Instant.parse("2026-03-03T09:00:00Z")
private val INSIDE_WINDOW: Instant = Instant.parse("2026-03-02T15:00:00Z")
private val ALREADY_STARTED: Instant = Instant.parse("2026-03-02T08:30:00Z")
private val PROPOSED: Instant = Instant.parse("2026-03-04T09:00:00Z")
private val ALREADY_PASSED: Instant = Instant.parse("2026-03-01T09:00:00Z")
private const val CLIENT_NAME = "Анна"
private const val SLOT_DURATION_MINUTES = 60
private const val SINGLE_SEAT = 1
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10
private const val SECONDS_IN_MINUTE = 60L

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
private fun <T> capturedBy(captor: ArgumentCaptor<*>): T = captor.capture() as T

class ChangeRequestTest {

    private val slotRepository = mock(TrainingSlotRepository::class.java)
    private val changeRequestRepository = mock(SlotChangeRequestRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val waitlistRepository = mock(SlotWaitlistRepository::class.java)
    private val roster = mock(SlotRoster::class.java)
    private val participantRepository = mock(SlotParticipantRepository::class.java)
    private val pushSender = mock(PushSender::class.java)

    private val seats = SlotSeats(
        participantRepository = participantRepository,
        waitlistRepository = waitlistRepository,
        coachRepository = coachRepository,
        pushSender = pushSender,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private val service = ScheduleService(
        slotRepository = slotRepository,
        changeRequestRepository = changeRequestRepository,
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        waitlistRepository = waitlistRepository,
        roster = roster,
        participantRepository = participantRepository,
        seats = seats,
        pushSender = pushSender,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private val changes = SlotChangeService(
        slotRepository = slotRepository,
        changeRequestRepository = changeRequestRepository,
        participantRepository = participantRepository,
        coachRepository = coachRepository,
        userRepository = userRepository,
        seats = seats,
        pushSender = pushSender,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `a cancellation ahead of the window frees the seat at once and tells the coach`() {
        val seat = givenBooked(startsAt = AHEAD_OF_WINDOW)

        val request = changes.requestChange(userId = CLIENT, slotId = SLOT_ID, body = cancel())

        assertEquals(SlotChangeStatus.APPROVED, request.status)
        verify(participantRepository).delete(seat)
        val pushed = ArgumentCaptor.forClass(PushMessage::class.java)
        verify(pushSender).send(anyNonNull(), capturedBy(pushed))
        assertEquals(PushText.CLIENT_CANCELLED, pushed.value.text)
        assertEquals(CLIENT_NAME, pushed.value.args.first())
    }

    @Test
    fun `a late cancellation waits for the coach and keeps the seat`() {
        val seat = givenBooked(startsAt = INSIDE_WINDOW)

        val request = changes.requestChange(userId = CLIENT, slotId = SLOT_ID, body = cancel())

        assertEquals(SlotChangeStatus.PENDING, request.status)
        verify(participantRepository, never()).delete(seat)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a late reschedule is refused`() {
        givenBooked(startsAt = INSIDE_WINDOW)

        val failure = assertFailsWith<ResponseStatusException> {
            changes.requestChange(userId = CLIENT, slotId = SLOT_ID, body = reschedule())
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
    }

    @Test
    fun `a session that has started takes no requests`() {
        givenBooked(startsAt = ALREADY_STARTED)

        val failure = assertFailsWith<ResponseStatusException> {
            changes.requestChange(userId = CLIENT, slotId = SLOT_ID, body = cancel())
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
    }

    @Test
    fun `a request remembers the time it was made about`() {
        givenBooked(startsAt = AHEAD_OF_WINDOW)

        changes.requestChange(userId = CLIENT, slotId = SLOT_ID, body = reschedule())

        val saved = ArgumentCaptor.forClass(SlotChangeRequestEntity::class.java)
        verify(changeRequestRepository).save(capturedBy<SlotChangeRequestEntity>(saved))
        assertEquals(AHEAD_OF_WINDOW, saved.value.originalStartsAt)
        assertEquals(SlotChangeStatus.PENDING, saved.value.status)
    }

    @Test
    fun `a reschedule to a time that has passed is refused`() {
        givenBooked(startsAt = AHEAD_OF_WINDOW)

        val failure = assertFailsWith<ResponseStatusException> {
            changes.requestChange(
                userId = CLIENT,
                slotId = SLOT_ID,
                body = SlotChangeRequestBody(kind = SlotChangeKind.RESCHEDULE, proposedStartsAt = ALREADY_PASSED),
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, failure.statusCode)
    }

    @Test
    fun `an approved reschedule into the coach's free slot moves the seat there`() {
        val booked = slot(startsAt = AHEAD_OF_WINDOW)
        val seat = givenPendingReschedule(booked)
        val free = slot(startsAt = PROPOSED, id = FREE_SLOT_ID)
        `when`(slotRepository.findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(COACH_ID, PROPOSED, PROPOSED))
            .thenReturn(listOf(free))
        `when`(slotRepository.findWithLockById(FREE_SLOT_ID)).thenReturn(free)

        val resolved = changes.resolveChange(
            coachUserId = COACH_USER_ID,
            requestId = REQUEST_ID,
            approve = true,
            comment = null,
        )

        verify(participantRepository).delete(seat)
        val taken = ArgumentCaptor.forClass(SlotParticipantEntity::class.java)
        verify(participantRepository).save(capturedBy<SlotParticipantEntity>(taken))
        assertEquals(FREE_SLOT_ID, taken.value.slotId)
        assertEquals(CLIENT, taken.value.userId)
        assertEquals(FREE_SLOT_ID, resolved.slotId)
        assertEquals(AHEAD_OF_WINDOW, booked.startsAt)
    }

    @Test
    fun `an approved reschedule to open time moves the session itself`() {
        val booked = slot(startsAt = AHEAD_OF_WINDOW)
        val seat = givenPendingReschedule(booked)

        val resolved = changes.resolveChange(
            coachUserId = COACH_USER_ID,
            requestId = REQUEST_ID,
            approve = true,
            comment = null,
        )

        assertEquals(PROPOSED, booked.startsAt)
        assertEquals(SLOT_ID, resolved.slotId)
        verify(participantRepository, never()).delete(seat)
    }

    @Test
    fun `the coach may move a session onto a time that is already booked`() {
        val booked = slot(startsAt = AHEAD_OF_WINDOW)
        val seat = givenPendingReschedule(booked)
        val taken = slot(startsAt = PROPOSED, id = FREE_SLOT_ID)
        `when`(slotRepository.findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(COACH_ID, PROPOSED, PROPOSED))
            .thenReturn(listOf(taken))
        `when`(slotRepository.findWithLockById(FREE_SLOT_ID)).thenReturn(taken)
        `when`(participantRepository.countBySlotId(FREE_SLOT_ID)).thenReturn(SINGLE_SEAT)

        val resolved = changes.resolveChange(
            coachUserId = COACH_USER_ID,
            requestId = REQUEST_ID,
            approve = true,
            comment = null,
        )

        assertEquals(PROPOSED, booked.startsAt)
        assertEquals(SLOT_ID, resolved.slotId)
        verify(participantRepository, never()).delete(seat)
    }

    @Test
    fun `a client withdraws their own pending request`() {
        givenBooked(startsAt = AHEAD_OF_WINDOW)
        val pending = request(status = SlotChangeStatus.PENDING, requestedBy = CLIENT)
        `when`(changeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pending))

        changes.withdrawChange(userId = CLIENT, requestId = REQUEST_ID)

        verify(changeRequestRepository).delete(pending)
    }

    @Test
    fun `someone else's request cannot be withdrawn`() {
        val pending = request(status = SlotChangeStatus.PENDING, requestedBy = STRANGER)
        `when`(changeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pending))

        val failure = assertFailsWith<ResponseStatusException> {
            changes.withdrawChange(userId = CLIENT, requestId = REQUEST_ID)
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
    }

    @Test
    fun `an answered request cannot be withdrawn`() {
        val answered = request(status = SlotChangeStatus.REJECTED, requestedBy = CLIENT)
        `when`(changeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(answered))

        val failure = assertFailsWith<ResponseStatusException> {
            changes.withdrawChange(userId = CLIENT, requestId = REQUEST_ID)
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
    }

    @Test
    fun `the coach's refusal keeps the reason and drops an empty one`() {
        val slot = slot(startsAt = AHEAD_OF_WINDOW)
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(slotRepository.findWithLockById(SLOT_ID)).thenReturn(slot)
        val withReason = request(status = SlotChangeStatus.PENDING, requestedBy = CLIENT)
        `when`(changeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(withReason))

        changes.resolveChange(
            coachUserId = COACH_USER_ID,
            requestId = REQUEST_ID,
            approve = false,
            comment = "  В среду весь вечер занят  ",
        )

        assertEquals("В среду весь вечер занят", withReason.coachComment)

        val blank = request(status = SlotChangeStatus.PENDING, requestedBy = CLIENT)
        `when`(changeRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(blank))

        changes.resolveChange(coachUserId = COACH_USER_ID, requestId = REQUEST_ID, approve = false, comment = "   ")

        assertNull(blank.coachComment)
    }

    @Test
    fun `the client sees how their latest request ended`() {
        val slot = slot(startsAt = AHEAD_OF_WINDOW)
        givenActiveClient()
        `when`(coachRepository.findById(COACH_ID)).thenReturn(Optional.of(coach()))
        `when`(slotRepository.findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(COACH_ID, NOW, PROPOSED))
            .thenReturn(listOf(slot))
        `when`(participantRepository.findBySlotIdIn(listOf(SLOT_ID))).thenReturn(listOf(participation()))
        val older = request(status = SlotChangeStatus.REJECTED, requestedBy = CLIENT, createdAt = NOW)
        val newer = request(
            status = SlotChangeStatus.REJECTED,
            requestedBy = CLIENT,
            createdAt = NOW.plusSeconds(SECONDS_IN_MINUTE),
            comment = "Это время уже занято",
        )
        `when`(changeRequestRepository.findBySlotIdInAndRequestedByUserId(listOf(SLOT_ID), CLIENT))
            .thenReturn(listOf(older, newer))

        val schedule = service.clientSchedule(userId = CLIENT, coachId = COACH_ID, from = NOW, to = PROPOSED)

        val shown = schedule.slots.single().changeRequest
        assertEquals(SlotChangeStatus.REJECTED, shown?.status)
        assertEquals("Это время уже занято", shown?.coachComment)
        assertEquals(AHEAD_OF_WINDOW, shown?.originalStartsAt)
    }

    private fun givenBooked(startsAt: Instant): SlotParticipantEntity {
        val slot = slot(startsAt = startsAt)
        val seat = participation()
        `when`(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot))
        `when`(participantRepository.findBySlotIdAndUserId(SLOT_ID, CLIENT)).thenReturn(seat)
        `when`(coachRepository.findById(COACH_ID)).thenReturn(Optional.of(coach()))
        `when`(userRepository.findById(CLIENT)).thenReturn(Optional.of(user()))
        `when`(changeRequestRepository.save(anyNonNull<SlotChangeRequestEntity>()))
            .thenAnswer { it.arguments.first() as SlotChangeRequestEntity }
        return seat
    }

    private fun givenPendingReschedule(booked: TrainingSlotEntity): SlotParticipantEntity {
        val seat = participation()
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(slotRepository.findWithLockById(SLOT_ID)).thenReturn(booked)
        `when`(participantRepository.findBySlotIdAndUserId(SLOT_ID, CLIENT)).thenReturn(seat)
        `when`(changeRequestRepository.findById(REQUEST_ID))
            .thenReturn(Optional.of(request(status = SlotChangeStatus.PENDING, requestedBy = CLIENT)))
        return seat
    }

    private fun givenActiveClient() {
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT)).thenReturn(
            CoachClientEntity(
                id = UUID.randomUUID(),
                coachId = COACH_ID,
                userId = CLIENT,
                status = CoachClientStatus.ACTIVE,
                createdAt = NOW,
            )
        )
    }

    private fun cancel(): SlotChangeRequestBody =
        SlotChangeRequestBody(kind = SlotChangeKind.CANCEL, proposedStartsAt = null)

    private fun reschedule(): SlotChangeRequestBody =
        SlotChangeRequestBody(kind = SlotChangeKind.RESCHEDULE, proposedStartsAt = PROPOSED)

    private fun request(
        status: SlotChangeStatus,
        requestedBy: UUID,
        createdAt: Instant = NOW,
        comment: String? = null,
    ): SlotChangeRequestEntity = SlotChangeRequestEntity(
        id = REQUEST_ID,
        slotId = SLOT_ID,
        requestedByUserId = requestedBy,
        kind = SlotChangeKind.RESCHEDULE,
        proposedStartsAt = PROPOSED,
        originalStartsAt = AHEAD_OF_WINDOW,
        status = status,
        createdAt = createdAt,
        resolvedAt = null,
        coachComment = comment,
    )

    private fun slot(startsAt: Instant, id: UUID = SLOT_ID): TrainingSlotEntity = TrainingSlotEntity(
        id = id,
        coachId = COACH_ID,
        startsAt = startsAt,
        durationMinutes = SLOT_DURATION_MINUTES,
        capacity = SINGLE_SEAT,
        lifecycle = SlotLifecycle.SCHEDULED,
        createdAt = NOW,
    )

    private fun participation(): SlotParticipantEntity = SlotParticipantEntity(
        id = UUID.randomUUID(),
        slotId = SLOT_ID,
        userId = CLIENT,
        createdAt = NOW,
    )

    private fun user(): UserEntity = UserEntity(
        id = CLIENT,
        displayName = CLIENT_NAME,
        phone = null,
        email = null,
        login = null,
        isOwner = false,
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
