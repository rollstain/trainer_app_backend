package app.trainer.backend.reminder

import app.trainer.backend.checkin.CheckInRepository
import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.push.PushSender
import app.trainer.backend.schedule.SlotLifecycle
import app.trainer.backend.schedule.SlotParticipantEntity
import app.trainer.backend.schedule.SlotParticipantRepository
import app.trainer.backend.schedule.TrainingSlotEntity
import app.trainer.backend.schedule.TrainingSlotRepository
import app.trainer.backend.traininglog.TrainingLogEntryEntity
import app.trainer.backend.traininglog.TrainingLogEntryRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private val COACH_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000001")
private val COACH_USER_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000002")
private val CLIENT_USER_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000003")
private val SLOT_ID: UUID = UUID.fromString("30000000-0000-0000-0000-000000000004")

private val MOSCOW_TEN_IN_THE_MORNING: Instant = Instant.parse("2026-03-02T07:00:00Z")
private val MOSCOW_ELEVEN_AT_NIGHT: Instant = Instant.parse("2026-03-02T20:00:00Z")
private const val COACH_ZONE = "Europe/Moscow"
private const val DEFAULT_REMINDER_HOUR = 10
private const val LATE_REMINDER_HOUR = 23
private const val CANCELLATION_WINDOW_HOURS = 12
private const val SLOT_DURATION_MINUTES = 60
private const val SINGLE_SEAT = 1
private const val AN_HOUR_IN_SECONDS = 3_600L

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

private fun <T> eqNonNull(value: T): T = ArgumentMatchers.eq(value) ?: value

class ReminderServiceTest {

    private val slotRepository = mock(TrainingSlotRepository::class.java)
    private val entryRepository = mock(TrainingLogEntryRepository::class.java)
    private val checkInRepository = mock(CheckInRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)
    private val reminderLogRepository = mock(ReminderLogRepository::class.java)
    private val participantRepository = mock(SlotParticipantRepository::class.java)
    private val pushSender = mock(PushSender::class.java)

    @Test
    fun `a booked session an hour away is reminded about once`() {
        val service = serviceAt(MOSCOW_TEN_IN_THE_MORNING)
        givenUpcomingSlot()
        `when`(
            reminderLogRepository.existsByUserIdAndKindAndSubject(
                CLIENT_USER_ID,
                ReminderKind.SESSION.name,
                SLOT_ID.toString(),
            )
        ).thenReturn(false)

        val sent = service.remindAboutSessions()

        assertEquals(1, sent)
        verify(pushSender).send(eqNonNull(listOf(CLIENT_USER_ID)), anyNonNull())
    }

    @Test
    fun `the same session is never reminded about twice`() {
        val service = serviceAt(MOSCOW_TEN_IN_THE_MORNING)
        givenUpcomingSlot()
        `when`(
            reminderLogRepository.existsByUserIdAndKindAndSubject(
                CLIENT_USER_ID,
                ReminderKind.SESSION.name,
                SLOT_ID.toString(),
            )
        ).thenReturn(true)

        val sent = service.remindAboutSessions()

        assertEquals(0, sent)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a slot nobody signed up for is nobody's session to remind about`() {
        val service = serviceAt(MOSCOW_TEN_IN_THE_MORNING)
        `when`(slotRepository.findByStartsAtBetweenOrderByStartsAtAsc(anyNonNull(), anyNonNull()))
            .thenReturn(listOf(slot()))
        `when`(coachRepository.findAllById(anyNonNull())).thenReturn(listOf(coach()))
        `when`(participantRepository.findBySlotIdIn(anyNonNull())).thenReturn(emptyList())

        val sent = service.remindAboutSessions()

        assertEquals(0, sent)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a coach who switched session reminders off warns nobody`() {
        val service = serviceAt(MOSCOW_TEN_IN_THE_MORNING)
        givenUpcomingSlot(coach = coach(sessionRemindersEnabled = false))

        val sent = service.remindAboutSessions()

        assertEquals(0, sent)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `an idle diary and idle measurements are both nudged at the chosen hour`() {
        val service = serviceAt(MOSCOW_TEN_IN_THE_MORNING)
        givenCoachWithClient()
        givenNothingRemindedYet()

        val sent = service.remindAboutEngagement()

        assertEquals(2, sent)
    }

    @Test
    fun `nothing is nudged outside the chosen hour`() {
        val service = serviceAt(MOSCOW_ELEVEN_AT_NIGHT)
        givenCoachWithClient()

        val sent = service.remindAboutEngagement()

        assertEquals(0, sent)
        verify(pushSender, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `the nudge follows the hour the coach chose`() {
        val service = serviceAt(MOSCOW_ELEVEN_AT_NIGHT)
        givenCoachWithClient(coach = coach(reminderHour = LATE_REMINDER_HOUR))
        givenNothingRemindedYet()

        val sent = service.remindAboutEngagement()

        assertEquals(2, sent)
    }

    @Test
    fun `a coach who switched the diary nudge off still asks for measurements`() {
        val service = serviceAt(MOSCOW_TEN_IN_THE_MORNING)
        givenCoachWithClient(coach = coach(diaryRemindersEnabled = false))
        givenNothingRemindedYet()

        val sent = service.remindAboutEngagement()

        assertEquals(1, sent)
    }

    @Test
    fun `a coach who switched both nudges off is never asked for clients`() {
        val service = serviceAt(MOSCOW_TEN_IN_THE_MORNING)
        givenCoachWithClient(
            coach = coach(diaryRemindersEnabled = false, checkInRemindersEnabled = false),
        )

        val sent = service.remindAboutEngagement()

        assertEquals(0, sent)
        verify(coachClientRepository, never()).findByCoachIdAndStatus(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a client who logged this week is left alone`() {
        val service = serviceAt(MOSCOW_TEN_IN_THE_MORNING)
        givenCoachWithClient()
        givenNothingRemindedYet()
        `when`(
            entryRepository.findByClientUserIdAndEntryDateBetweenOrderByEntryDateDesc(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
            )
        ).thenReturn(listOf(entry(LocalDate.of(2026, 3, 1))))

        val sent = service.remindAboutEngagement()

        assertEquals(1, sent)
    }

    private fun serviceAt(now: Instant): ReminderService = ReminderService(
        slotRepository = slotRepository,
        entryRepository = entryRepository,
        checkInRepository = checkInRepository,
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        participantRepository = participantRepository,
        reminderLogRepository = reminderLogRepository,
        pushSender = pushSender,
        clock = Clock.fixed(now, ZoneOffset.UTC),
    )

    private fun givenUpcomingSlot(coach: CoachEntity = coach()) {
        `when`(slotRepository.findByStartsAtBetweenOrderByStartsAtAsc(anyNonNull(), anyNonNull()))
            .thenReturn(listOf(slot()))
        `when`(participantRepository.findBySlotIdIn(anyNonNull())).thenReturn(listOf(participation(CLIENT_USER_ID)))
        `when`(coachRepository.findAllById(eqNonNull(listOf(COACH_ID)))).thenReturn(listOf(coach))
    }

    private fun givenCoachWithClient(coach: CoachEntity = coach()) {
        `when`(coachRepository.findAll()).thenReturn(listOf(coach))
        `when`(coachClientRepository.findByCoachIdAndStatus(COACH_ID, CoachClientStatus.ACTIVE))
            .thenReturn(listOf(link()))
    }

    private fun givenNothingRemindedYet() {
        `when`(reminderLogRepository.existsByUserIdAndKindAndSubject(anyNonNull(), anyNonNull(), anyNonNull()))
            .thenReturn(false)
    }

    private fun coach(
        reminderHour: Int = DEFAULT_REMINDER_HOUR,
        sessionRemindersEnabled: Boolean = true,
        diaryRemindersEnabled: Boolean = true,
        checkInRemindersEnabled: Boolean = true,
    ): CoachEntity = CoachEntity(
        id = COACH_ID,
        userId = COACH_USER_ID,
        zoneId = COACH_ZONE,
        cancellationWindowHours = CANCELLATION_WINDOW_HOURS,
        reminderHour = reminderHour,
        sessionRemindersEnabled = sessionRemindersEnabled,
        diaryRemindersEnabled = diaryRemindersEnabled,
        checkInRemindersEnabled = checkInRemindersEnabled,
        createdAt = MOSCOW_TEN_IN_THE_MORNING,
    )

    private fun link(): CoachClientEntity = CoachClientEntity(
        id = UUID.fromString("30000000-0000-0000-0000-000000000005"),
        coachId = COACH_ID,
        userId = CLIENT_USER_ID,
        status = CoachClientStatus.ACTIVE,
        createdAt = MOSCOW_TEN_IN_THE_MORNING,
    )

    private fun slot(
        lifecycle: SlotLifecycle = SlotLifecycle.SCHEDULED,
    ): TrainingSlotEntity = TrainingSlotEntity(
        id = SLOT_ID,
        coachId = COACH_ID,
        startsAt = MOSCOW_TEN_IN_THE_MORNING.plusSeconds(AN_HOUR_IN_SECONDS),
        durationMinutes = SLOT_DURATION_MINUTES,
        capacity = SINGLE_SEAT,
        lifecycle = lifecycle,
        createdAt = MOSCOW_TEN_IN_THE_MORNING,
    )

    private fun participation(userId: UUID): SlotParticipantEntity = SlotParticipantEntity(
        id = UUID.randomUUID(),
        slotId = SLOT_ID,
        userId = userId,
        createdAt = MOSCOW_TEN_IN_THE_MORNING,
    )

    private fun entry(date: LocalDate): TrainingLogEntryEntity = TrainingLogEntryEntity(
        id = UUID.fromString("30000000-0000-0000-0000-000000000006"),
        clientUserId = CLIENT_USER_ID,
        entryDate = date,
        slotId = null,
        notes = null,
        createdAt = MOSCOW_TEN_IN_THE_MORNING,
        updatedAt = MOSCOW_TEN_IN_THE_MORNING,
    )
}
