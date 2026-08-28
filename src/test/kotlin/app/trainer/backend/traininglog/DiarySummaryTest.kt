package app.trainer.backend.traininglog

import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val COACH_USER_ID: UUID = UUID.fromString("60000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("60000000-0000-0000-0000-000000000002")
private val ANNA_ID: UUID = UUID.fromString("60000000-0000-0000-0000-000000000003")
private val BORIS_ID: UUID = UUID.fromString("60000000-0000-0000-0000-000000000004")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private val WINDOW_FROM: LocalDate = LocalDate.of(2026, 2, 17)
private val WINDOW_TO: LocalDate = LocalDate.of(2026, 3, 2)
private val ANNA_TRAINED_ON: LocalDate = LocalDate.of(2026, 2, 20)
private val ANNA_LAST_ENTRY: LocalDate = LocalDate.of(2026, 2, 25)
private const val ANNA_VOLUME_GRAMS = 1_800_000L
private const val WINDOW_HOURS = 12
private const val MORNING_HOUR = 10

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class DiarySummaryTest {

    private val exerciseRepository = mock(ExerciseRepository::class.java)
    private val entryRepository = mock(TrainingLogEntryRepository::class.java)
    private val setRepository = mock(TrainingLogSetRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val mediaFileService = mock(app.trainer.backend.media.MediaFileService::class.java)

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
    fun `the whole roster is summarised without a query per client`() {
        givenRoster()
        `when`(entryRepository.findDiaryDays(anyNonNull(), anyNonNull(), anyNonNull()))
            .thenReturn(listOf(diaryDay(ANNA_ID, ANNA_TRAINED_ON, ANNA_VOLUME_GRAMS)))
        `when`(entryRepository.findLastEntryDates(anyNonNull()))
            .thenReturn(listOf(lastEntry(ANNA_ID, ANNA_LAST_ENTRY)))

        val summary = service.diarySummary(coachUserId = COACH_USER_ID, from = WINDOW_FROM, to = WINDOW_TO)

        assertEquals(listOf("Анна", "Борис"), summary.map { it.displayName })
        verify(entryRepository, times(1)).findDiaryDays(anyNonNull(), anyNonNull(), anyNonNull())
        verify(entryRepository, times(1)).findLastEntryDates(anyNonNull())
        verify(entryRepository, times(0))
            .findByClientUserIdAndEntryDateBetweenOrderByEntryDateDesc(anyNonNull(), anyNonNull(), anyNonNull())
    }

    @Test
    fun `days and volume land on the client they belong to`() {
        givenRoster()
        `when`(entryRepository.findDiaryDays(anyNonNull(), anyNonNull(), anyNonNull()))
            .thenReturn(listOf(diaryDay(ANNA_ID, ANNA_TRAINED_ON, ANNA_VOLUME_GRAMS)))
        `when`(entryRepository.findLastEntryDates(anyNonNull()))
            .thenReturn(listOf(lastEntry(ANNA_ID, ANNA_LAST_ENTRY)))

        val summary = service.diarySummary(coachUserId = COACH_USER_ID, from = WINDOW_FROM, to = WINDOW_TO)

        val anna = summary.single { it.clientUserId == ANNA_ID }
        assertEquals(listOf(ANNA_TRAINED_ON), anna.days.map { it.entryDate })
        assertEquals(ANNA_VOLUME_GRAMS, anna.days.single().volumeGrams)
        assertEquals(ANNA_LAST_ENTRY, anna.lastEntryDate)

        val boris = summary.single { it.clientUserId == BORIS_ID }
        assertTrue(boris.days.isEmpty(), "у Бориса записей нет — и дней быть не должно")
        assertNull(boris.lastEntryDate, "никогда не заполнял — даты нет")
    }

    @Test
    fun `the last entry may be older than the window`() {
        givenRoster()
        `when`(entryRepository.findDiaryDays(anyNonNull(), anyNonNull(), anyNonNull())).thenReturn(emptyList())
        `when`(entryRepository.findLastEntryDates(anyNonNull()))
            .thenReturn(listOf(lastEntry(ANNA_ID, LocalDate.of(2025, 11, 4))))

        val summary = service.diarySummary(coachUserId = COACH_USER_ID, from = WINDOW_FROM, to = WINDOW_TO)

        val anna = summary.single { it.clientUserId == ANNA_ID }
        assertTrue(anna.days.isEmpty())
        assertEquals(LocalDate.of(2025, 11, 4), anna.lastEntryDate, "иначе давний ученик выглядит как новичок")
    }

    @Test
    fun `an empty roster asks the database nothing`() {
        givenCoach()
        `when`(coachClientRepository.findActiveOrdered(COACH_ID)).thenReturn(emptyList())

        val summary = service.diarySummary(coachUserId = COACH_USER_ID, from = WINDOW_FROM, to = WINDOW_TO)

        assertTrue(summary.isEmpty())
        verify(entryRepository, times(0)).findDiaryDays(anyNonNull(), anyNonNull(), anyNonNull())
    }

    @Test
    fun `a user who is not a coach gets nothing`() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.diarySummary(coachUserId = COACH_USER_ID, from = WINDOW_FROM, to = WINDOW_TO)
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
                createdAt = NOW,
            )
        )
    }

    private fun givenRoster() {
        givenCoach()
        `when`(coachClientRepository.findActiveOrdered(COACH_ID))
            .thenReturn(listOf(link(ANNA_ID), link(BORIS_ID)))
        `when`(userRepository.findAllById(anyNonNull())).thenReturn(
            listOf(user(ANNA_ID, "Анна"), user(BORIS_ID, "Борис"))
        )
    }

    private fun link(userId: UUID): CoachClientEntity = CoachClientEntity(
        id = UUID.randomUUID(),
        coachId = COACH_ID,
        userId = userId,
        status = CoachClientStatus.ACTIVE,
        createdAt = NOW,
    )

    private fun user(userId: UUID, displayName: String): UserEntity = UserEntity(
        id = userId,
        displayName = displayName,
        phone = null,
        email = null,
        createdAt = NOW,
    )

    private fun diaryDay(clientUserId: UUID, entryDate: LocalDate, volumeGrams: Long): ClientDiaryDay =
        object : ClientDiaryDay {
            override fun getClientUserId(): UUID = clientUserId
            override fun getEntryDate(): LocalDate = entryDate
            override fun getVolumeGrams(): Long = volumeGrams
        }

    private fun lastEntry(clientUserId: UUID, lastEntryDate: LocalDate): ClientLastEntry =
        object : ClientLastEntry {
            override fun getClientUserId(): UUID = clientUserId
            override fun getLastEntryDate(): LocalDate = lastEntryDate
        }
}
