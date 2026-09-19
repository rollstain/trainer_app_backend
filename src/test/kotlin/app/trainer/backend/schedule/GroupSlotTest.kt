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
import kotlin.test.assertFalse
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

private val COACH_USER_ID: UUID = UUID.fromString("90000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("90000000-0000-0000-0000-000000000002")
private val SLOT_ID: UUID = UUID.fromString("90000000-0000-0000-0000-000000000003")
private val FIRST_CLIENT: UUID = UUID.fromString("90000000-0000-0000-0000-000000000004")
private val SECOND_CLIENT: UUID = UUID.fromString("90000000-0000-0000-0000-000000000005")
private val THIRD_CLIENT: UUID = UUID.fromString("90000000-0000-0000-0000-000000000006")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private val SLOT_STARTS_AT: Instant = Instant.parse("2026-03-03T09:00:00Z")
private const val SLOT_STARTS_AT_IN_COACH_ZONE = "03.03 12:00"
private const val SLOT_DURATION_MINUTES = 60
private const val SECONDS_IN_HOUR = 3600L
private const val GROUP_SEATS = 2
private const val SINGLE_SEAT = 1
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
private fun <T> capturedBy(captor: ArgumentCaptor<*>): T = captor.capture() as T

class GroupSlotTest {

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
    fun `a second client joins a group session`() {
        val slot = slot(capacity = GROUP_SEATS)
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT))

        val booked = service.book(userId = SECOND_CLIENT, slotId = SLOT_ID)

        assertEquals(GROUP_SEATS, booked.capacity)
        verify(participantRepository).save(anyNonNull())
    }

    @Test
    fun `a full group turns the slot away`() {
        val slot = slot(capacity = GROUP_SEATS)
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT, SECOND_CLIENT))

        val failure = assertFailsWith<ResponseStatusException> {
            service.book(userId = THIRD_CLIENT, slotId = SLOT_ID)
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
        verify(participantRepository, never()).save(anyNonNull())
    }

    @Test
    fun `nobody books the same session twice`() {
        val slot = slot(capacity = GROUP_SEATS)
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT))
        `when`(participantRepository.findBySlotIdAndUserId(SLOT_ID, FIRST_CLIENT))
            .thenReturn(participation(FIRST_CLIENT))

        val failure = assertFailsWith<ResponseStatusException> {
            service.book(userId = FIRST_CLIENT, slotId = SLOT_ID)
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
    }

    @Test
    fun `a personal slot still holds exactly one client`() {
        val slot = slot(capacity = SINGLE_SEAT)
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT))

        val failure = assertFailsWith<ResponseStatusException> {
            service.book(userId = SECOND_CLIENT, slotId = SLOT_ID)
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode, "личное занятие занято")
    }

    @Test
    fun `a free seat is what the waitlist waits for`() {
        val slot = slot(capacity = GROUP_SEATS)
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT, SECOND_CLIENT))
        `when`(participantRepository.findBySlotIdAndUserId(SLOT_ID, FIRST_CLIENT))
            .thenReturn(participation(FIRST_CLIENT))
        `when`(waitlistRepository.findBySlotIdOrderByCreatedAtAsc(SLOT_ID))
            .thenReturn(listOf(waiting(UUID.randomUUID())))
        `when`(slotRepository.findParticipatedAfter(anyNonNull(), anyNonNull(), anyNonNull()))
            .thenReturn(listOf(slot))

        service.releaseBookingsOf(coachId = COACH_ID, clientUserId = FIRST_CLIENT)

        verify(participantRepository).delete(anyNonNull())
        verify(pushSender).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a group session is not moved by one of its clients`() {
        val slot = slot(capacity = GROUP_SEATS)
        `when`(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot))
        `when`(participantRepository.findBySlotIdAndUserId(SLOT_ID, FIRST_CLIENT))
            .thenReturn(participation(FIRST_CLIENT))

        val failure = assertFailsWith<ResponseStatusException> {
            changes.requestChange(
                userId = FIRST_CLIENT,
                slotId = SLOT_ID,
                body = SlotChangeRequestBody(
                    kind = SlotChangeKind.RESCHEDULE,
                    proposedStartsAt = SLOT_STARTS_AT.plusSeconds(SECONDS_IN_HOUR),
                ),
            )
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
    }

    @Test
    fun `a client leaves a group session without moving it`() {
        val slot = slot(capacity = GROUP_SEATS)
        val seat = participation(FIRST_CLIENT)
        `when`(slotRepository.findById(SLOT_ID)).thenReturn(Optional.of(slot))
        `when`(participantRepository.findBySlotIdAndUserId(SLOT_ID, FIRST_CLIENT)).thenReturn(seat)
        `when`(coachRepository.findById(COACH_ID)).thenReturn(Optional.of(coach()))
        `when`(changeRequestRepository.save(anyNonNull<SlotChangeRequestEntity>()))
            .thenAnswer { it.arguments.first() as SlotChangeRequestEntity }

        val request = changes.requestChange(
            userId = FIRST_CLIENT,
            slotId = SLOT_ID,
            body = SlotChangeRequestBody(kind = SlotChangeKind.CANCEL, proposedStartsAt = null),
        )

        assertEquals(SlotChangeStatus.APPROVED, request.status, "до окна отмены место освобождается сразу")
        assertEquals(SLOT_STARTS_AT, request.slotStartsAt, "время занятия остаётся прежним")
        verify(participantRepository).delete(seat)
    }

    @Test
    fun `the coach signs a client up and frees the seat back`() {
        val slot = slot(capacity = GROUP_SEATS)
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT))
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(participantRepository.findBySlotIdAndUserId(SLOT_ID, FIRST_CLIENT))
            .thenReturn(participation(FIRST_CLIENT))
        `when`(waitlistRepository.findBySlotIdOrderByCreatedAtAsc(SLOT_ID)).thenReturn(emptyList())

        service.removeParticipant(
            coachUserId = COACH_USER_ID,
            slotId = SLOT_ID,
            clientUserId = FIRST_CLIENT,
        )

        verify(participantRepository).delete(anyNonNull())
    }

    @Test
    fun `removing someone who never signed up is nothing to do`() {
        val slot = slot(capacity = GROUP_SEATS)
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT))
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(participantRepository.findBySlotIdAndUserId(SLOT_ID, THIRD_CLIENT)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.removeParticipant(
                coachUserId = COACH_USER_ID,
                slotId = SLOT_ID,
                clientUserId = THIRD_CLIENT,
            )
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    @Test
    fun `a client the coach signs up hears about it`() {
        val slot = slot(capacity = GROUP_SEATS)
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT))
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        val recipients = ArgumentCaptor.forClass(Collection::class.java)
        val message = ArgumentCaptor.forClass(PushMessage::class.java)

        service.assignSlot(coachUserId = COACH_USER_ID, slotId = SLOT_ID, clientUserId = SECOND_CLIENT)

        verify(participantRepository).save(anyNonNull())
        verify(pushSender).send(capturedBy(recipients), capturedBy(message))
        assertEquals(listOf(SECOND_CLIENT), recipients.value.toList())
        assertEquals(PushText.SLOT_ASSIGNED, message.value.text)
        assertEquals(listOf(SLOT_STARTS_AT_IN_COACH_ZONE), message.value.args, "время в поясе тренера")
        assertEquals(mapOf("slotId" to SLOT_ID.toString()), message.value.data)
    }

    @Test
    fun `signing someone up after the session started sends no push`() {
        val slot = slot(capacity = GROUP_SEATS, startsAt = NOW.minusSeconds(SECONDS_IN_HOUR))
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT))
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())

        service.assignSlot(coachUserId = COACH_USER_ID, slotId = SLOT_ID, clientUserId = SECOND_CLIENT)

        verify(participantRepository).save(anyNonNull())
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `cancelling a session tells everyone who signed up when it was`() {
        val slot = slot(capacity = GROUP_SEATS)
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT, SECOND_CLIENT))
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        val recipients = ArgumentCaptor.forClass(Collection::class.java)
        val message = ArgumentCaptor.forClass(PushMessage::class.java)

        service.cancelSlot(coachUserId = COACH_USER_ID, slotId = SLOT_ID)

        verify(pushSender).send(capturedBy(recipients), capturedBy(message))
        assertEquals(listOf(FIRST_CLIENT, SECOND_CLIENT), recipients.value.toList())
        assertEquals(PushText.SLOT_CANCELLED, message.value.text)
        assertEquals(listOf(SLOT_STARTS_AT_IN_COACH_ZONE), message.value.args, "время в поясе тренера")
        assertEquals(mapOf("slotId" to SLOT_ID.toString()), message.value.data)
    }

    @Test
    fun `an empty session is cancelled without a push`() {
        val slot = slot(capacity = GROUP_SEATS)
        givenSlot(slot, takenBy = emptyList())
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())

        service.cancelSlot(coachUserId = COACH_USER_ID, slotId = SLOT_ID)

        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a session cancelled again is not announced again`() {
        val slot = slot(capacity = GROUP_SEATS, lifecycle = SlotLifecycle.CANCELLED)
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT))
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())

        service.cancelSlot(coachUserId = COACH_USER_ID, slotId = SLOT_ID)

        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a session that has already started is cancelled quietly`() {
        val slot = slot(capacity = GROUP_SEATS, startsAt = NOW.minusSeconds(SECONDS_IN_HOUR))
        givenSlot(slot, takenBy = listOf(FIRST_CLIENT))
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())

        service.cancelSlot(coachUserId = COACH_USER_ID, slotId = SLOT_ID)

        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `the coach sees who signed up and how many seats are left`() {
        val slot = slot(capacity = GROUP_SEATS)
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(
            slotRepository.findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
            )
        ).thenReturn(listOf(slot))
        `when`(changeRequestRepository.findBySlotIdInAndStatus(anyNonNull(), anyNonNull())).thenReturn(emptyList())
        `when`(roster.participantsOf(anyNonNull<List<TrainingSlotEntity>>()))
            .thenReturn(mapOf(slot.id to listOf(bookedSeat(FIRST_CLIENT))))
        `when`(roster.waitlistOf(anyNonNull<List<TrainingSlotEntity>>())).thenReturn(emptyMap())

        val schedule = service.coachSchedule(coachUserId = COACH_USER_ID, from = NOW, to = SLOT_STARTS_AT)

        val first = schedule.slots.single()
        assertEquals(1, first.takenSeats)
        assertEquals(GROUP_SEATS, first.capacity)
        assertEquals(listOf("Анна"), first.participants.map { it.displayName })
        assertEquals(SlotStatus.FREE, first.status, "место ещё есть — слот открыт для записи")
    }

    @Test
    fun `a client counts seats but never sees the names`() {
        val slot = slot(capacity = GROUP_SEATS)
        `when`(coachRepository.findById(COACH_ID)).thenReturn(Optional.of(coach()))
        givenActiveClient(SECOND_CLIENT)
        `when`(
            slotRepository.findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
            )
        ).thenReturn(listOf(slot))
        `when`(changeRequestRepository.findBySlotIdInAndStatus(anyNonNull(), anyNonNull())).thenReturn(emptyList())
        `when`(participantRepository.findBySlotIdIn(anyNonNull())).thenReturn(listOf(participation(FIRST_CLIENT)))
        `when`(waitlistRepository.findBySlotIdInAndUserId(anyNonNull(), anyNonNull())).thenReturn(emptyList())

        val schedule = service.clientSchedule(
            userId = SECOND_CLIENT,
            coachId = COACH_ID,
            from = NOW,
            to = SLOT_STARTS_AT,
        )

        val first = schedule.slots.single()
        assertEquals(1, first.takenSeats)
        assertEquals(GROUP_SEATS, first.capacity)
        assertTrue(first.isAvailable, "одно место свободно")
        assertFalse(first.isBookedByMe, "записан другой клиент")
    }

    @Test
    fun `a client in the waitlist sees their place in line`() {
        val slot = slot(capacity = SINGLE_SEAT)
        `when`(coachRepository.findById(COACH_ID)).thenReturn(Optional.of(coach()))
        givenActiveClient(SECOND_CLIENT)
        `when`(
            slotRepository.findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
            )
        ).thenReturn(listOf(slot))
        `when`(changeRequestRepository.findBySlotIdInAndStatus(anyNonNull(), anyNonNull())).thenReturn(emptyList())
        `when`(participantRepository.findBySlotIdIn(anyNonNull())).thenReturn(listOf(participation(FIRST_CLIENT)))
        `when`(roster.waitlistPositionsOf(anyNonNull(), anyNonNull())).thenReturn(mapOf(SLOT_ID to 2))

        val schedule = service.clientSchedule(
            userId = SECOND_CLIENT,
            coachId = COACH_ID,
            from = NOW,
            to = SLOT_STARTS_AT,
        )

        val first = schedule.slots.single()
        assertTrue(first.isOnWaitlist)
        assertEquals(2, first.waitlistPosition, "впереди один человек")
    }

    @Test
    fun `a client outside the waitlist has no place in line`() {
        val slot = slot(capacity = SINGLE_SEAT)
        `when`(coachRepository.findById(COACH_ID)).thenReturn(Optional.of(coach()))
        givenActiveClient(SECOND_CLIENT)
        `when`(
            slotRepository.findByCoachIdAndStartsAtBetweenOrderByStartsAtAsc(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
            )
        ).thenReturn(listOf(slot))
        `when`(changeRequestRepository.findBySlotIdInAndStatus(anyNonNull(), anyNonNull())).thenReturn(emptyList())
        `when`(participantRepository.findBySlotIdIn(anyNonNull())).thenReturn(listOf(participation(FIRST_CLIENT)))
        `when`(waitlistRepository.findBySlotIdInAndUserId(anyNonNull(), anyNonNull())).thenReturn(emptyList())

        val schedule = service.clientSchedule(
            userId = SECOND_CLIENT,
            coachId = COACH_ID,
            from = NOW,
            to = SLOT_STARTS_AT,
        )

        val first = schedule.slots.single()
        assertFalse(first.isOnWaitlist)
        assertEquals(null, first.waitlistPosition)
    }

    private fun givenSlot(slot: TrainingSlotEntity, takenBy: List<UUID>) {
        `when`(slotRepository.findWithLockById(SLOT_ID)).thenReturn(slot)
        `when`(participantRepository.countBySlotId(SLOT_ID)).thenReturn(takenBy.size)
        `when`(participantRepository.findBySlotId(SLOT_ID)).thenReturn(takenBy.map(::participation))
        `when`(coachRepository.findById(COACH_ID)).thenReturn(Optional.of(coach()))
        `when`(userRepository.findAllById(anyNonNull())).thenReturn(takenBy.map(::user))
        givenActiveClient(FIRST_CLIENT)
        givenActiveClient(SECOND_CLIENT)
        givenActiveClient(THIRD_CLIENT)
    }

    private fun givenActiveClient(userId: UUID) {
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, userId)).thenReturn(
            CoachClientEntity(
                id = UUID.randomUUID(),
                coachId = COACH_ID,
                userId = userId,
                status = CoachClientStatus.ACTIVE,
                createdAt = NOW,
            )
        )
    }

    private fun slot(
        capacity: Int,
        startsAt: Instant = SLOT_STARTS_AT,
        lifecycle: SlotLifecycle = SlotLifecycle.SCHEDULED,
    ): TrainingSlotEntity = TrainingSlotEntity(
        id = SLOT_ID,
        coachId = COACH_ID,
        startsAt = startsAt,
        durationMinutes = SLOT_DURATION_MINUTES,
        capacity = capacity,
        lifecycle = lifecycle,
        createdAt = NOW,
    )

    private fun bookedSeat(userId: UUID): SlotParticipantResponse = SlotParticipantResponse(
        userId = userId,
        displayName = user(userId).displayName,
        bookedAt = NOW,
        hasMedicalNotes = false,
    )

    private fun participation(userId: UUID): SlotParticipantEntity = SlotParticipantEntity(
        id = UUID.randomUUID(),
        slotId = SLOT_ID,
        userId = userId,
        createdAt = NOW,
    )

    private fun waiting(userId: UUID): SlotWaitlistEntity = SlotWaitlistEntity(
        id = UUID.randomUUID(),
        slotId = SLOT_ID,
        userId = userId,
        createdAt = NOW,
        notifiedAt = null,
    )

    private fun user(userId: UUID): UserEntity = UserEntity(
        id = userId,
        displayName = "Анна",
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
