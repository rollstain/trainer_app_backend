package app.trainer.backend.schedule

import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.push.PushSender
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

private val COACH_USER_ID: UUID = UUID.fromString("d0000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("d0000000-0000-0000-0000-000000000002")
private val CLIENT: UUID = UUID.fromString("d0000000-0000-0000-0000-000000000003")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10
private const val A_DAY_IN_SECONDS = 86_400L

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

private class Participation(
    private val clientUserId: UUID,
    private val startsAt: Instant,
    private val status: SlotLifecycle,
) : PastParticipation {

    override fun getClientUserId(): UUID = clientUserId

    override fun getStartsAt(): Instant = startsAt

    override fun getStatus(): String = status.name
}

class MissedSessionsTest {

    private val slotRepository = mock(TrainingSlotRepository::class.java)
    private val changeRequestRepository = mock(SlotChangeRequestRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val waitlistRepository = mock(SlotWaitlistRepository::class.java)
    private val roster = mock(SlotRoster::class.java)
    private val participantRepository = mock(SlotParticipantRepository::class.java)
    private val pushSender = mock(PushSender::class.java)

    private val service = ScheduleService(
        slotRepository = slotRepository,
        changeRequestRepository = changeRequestRepository,
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        userRepository = userRepository,
        waitlistRepository = waitlistRepository,
        roster = roster,
        participantRepository = participantRepository,
        pushSender = pushSender,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `two sessions in a row nobody marked as done are two misses`() {
        givenPast(
            participation(daysAgo = 1, status = SlotLifecycle.SCHEDULED),
            participation(daysAgo = 4, status = SlotLifecycle.SCHEDULED),
            participation(daysAgo = 8, status = SlotLifecycle.COMPLETED),
        )

        val missed = service.missedSessionsByClient(coachUserId = COACH_USER_ID, clientUserIds = listOf(CLIENT))

        assertEquals(2, missed[CLIENT])
    }

    @Test
    fun `a session that did happen stops the count`() {
        givenPast(
            participation(daysAgo = 1, status = SlotLifecycle.COMPLETED),
            participation(daysAgo = 4, status = SlotLifecycle.SCHEDULED),
        )

        val missed = service.missedSessionsByClient(coachUserId = COACH_USER_ID, clientUserIds = listOf(CLIENT))

        assertTrue(missed.isEmpty(), "последняя тренировка состоялась — человек не пропадал")
    }

    @Test
    fun `a cancelled session is not a miss`() {
        givenPast(
            participation(daysAgo = 1, status = SlotLifecycle.CANCELLED),
            participation(daysAgo = 4, status = SlotLifecycle.COMPLETED),
        )

        val missed = service.missedSessionsByClient(coachUserId = COACH_USER_ID, clientUserIds = listOf(CLIENT))

        assertTrue(missed.isEmpty(), "отменённое занятие никто не пропускал")
    }

    @Test
    fun `a client without past sessions is not counted`() {
        givenPast()

        val missed = service.missedSessionsByClient(coachUserId = COACH_USER_ID, clientUserIds = listOf(CLIENT))

        assertTrue(missed.isEmpty())
    }

    private fun givenPast(vararg participation: PastParticipation) {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
        `when`(participantRepository.findPastParticipation(anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull()))
            .thenReturn(participation.toList())
    }

    private fun participation(daysAgo: Long, status: SlotLifecycle): PastParticipation = Participation(
        clientUserId = CLIENT,
        startsAt = NOW.minusSeconds(daysAgo * A_DAY_IN_SECONDS),
        status = status,
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
