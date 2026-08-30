package app.trainer.backend.coach

import app.trainer.backend.clientnotes.ClientNoteRepository
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.schedule.ScheduleService
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private val PAGE_COACH_USER_ID: UUID = UUID.fromString("50000000-0000-0000-0000-000000000001")
private val PAGE_COACH_ID: UUID = UUID.fromString("50000000-0000-0000-0000-000000000002")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private const val WINDOW_HOURS = 12
private const val MORNING_HOUR = 10
private const val PAGE_SIZE = 2

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class CoachClientsPageTest {

    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val clientNoteRepository = mock(ClientNoteRepository::class.java)
    private val workingHourRepository = mock(CoachWorkingHourRepository::class.java)
    private val scheduleService = mock(ScheduleService::class.java)

    private val service = CoachService(
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        userRepository = userRepository,
        clientNoteRepository = clientNoteRepository,
        workingHourRepository = workingHourRepository,
        scheduleService = scheduleService,
    )

    @Test
    fun `without a limit the whole roster comes back and no cursor is offered`() {
        givenCoach()
        givenRoster(ordered = listOf(client("Анна"), client("Борис"), client("Вера")))

        val page = service.clientsOfCoach(
            coachUserId = PAGE_COACH_USER_ID,
            limit = null,
            after = null,
            userIds = null,
            query = null,
        )

        assertEquals(listOf("Анна", "Борис", "Вера"), page.items.map { it.displayName })
        assertNull(page.nextCursor, "без limit подкачивать нечего")
        verify(coachClientRepository, never())
            .findActivePage(anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull(), ArgumentMatchers.anyInt())
    }

    @Test
    fun `the row fetched beyond the page only signals that more exist`() {
        givenCoach()
        val first = client("Анна")
        val second = client("Борис")
        val beyondThePage = client("Вера")
        givenPage(page = listOf(first, second, beyondThePage))

        val page = service.clientsOfCoach(
            coachUserId = PAGE_COACH_USER_ID,
            limit = PAGE_SIZE,
            after = null,
            userIds = null,
            query = null,
        )

        assertEquals(listOf("Анна", "Борис"), page.items.map { it.displayName }, "лишняя строка наружу не уходит")
        val cursor = assertNotNull(decodeCursor(page.nextCursor))
        assertEquals("Борис", cursor.sortKey, "курсор указывает на последнего показанного")
        assertEquals(second.userId, cursor.id)
    }

    @Test
    fun `a short page means the roster is over`() {
        givenCoach()
        givenPage(page = listOf(client("Анна")))

        val page = service.clientsOfCoach(
            coachUserId = PAGE_COACH_USER_ID,
            limit = PAGE_SIZE,
            after = null,
            userIds = null,
            query = null,
        )

        assertEquals(1, page.items.size)
        assertNull(page.nextCursor, "страница неполная — дальше ничего нет")
    }

    @Test
    fun `a picked handful of clients comes back whole, without paging`() {
        givenCoach()
        val anna = client("Анна")
        val vera = client("Вера")
        `when`(coachClientRepository.findActiveByUserIds(anyNonNull(), anyNonNull()))
            .thenReturn(listOf(anna, vera))
        givenUsersFor(listOf(anna, vera))

        val page = service.clientsOfCoach(
            coachUserId = PAGE_COACH_USER_ID,
            limit = PAGE_SIZE,
            after = null,
            userIds = listOf(anna.userId, vera.userId),
            query = null,
        )

        assertEquals(listOf("Анна", "Вера"), page.items.map { it.displayName })
        assertNull(page.nextCursor, "выборка по id не листается")
        verify(coachClientRepository, never())
            .findActivePage(anyNonNull(), anyNonNull(), anyNonNull(), anyNonNull(), ArgumentMatchers.anyInt())
    }

    private fun givenCoach() {
        `when`(coachRepository.findByUserId(PAGE_COACH_USER_ID)).thenReturn(
            CoachEntity(
                id = PAGE_COACH_ID,
                userId = PAGE_COACH_USER_ID,
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

    private fun givenRoster(ordered: List<CoachClientEntity>) {
        `when`(coachClientRepository.findActiveOrdered(PAGE_COACH_ID)).thenReturn(ordered)
        givenUsersFor(ordered)
    }

    private fun givenPage(page: List<CoachClientEntity>) {
        `when`(
            coachClientRepository.findActivePage(
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                anyNonNull(),
                ArgumentMatchers.anyInt(),
            )
        ).thenReturn(page)
        givenUsersFor(page)
    }

    private fun givenUsersFor(links: List<CoachClientEntity>) {
        val users = links.map { link ->
            UserEntity(
                id = link.userId,
                displayName = namesById.getValue(link.userId),
                phone = null,
                email = null,
                login = null,
                isOwner = false,
                createdAt = NOW,
            )
        }
        `when`(userRepository.findAllById(anyNonNull())).thenReturn(users)
    }

    private val namesById = mutableMapOf<UUID, String>()

    private fun client(displayName: String): CoachClientEntity {
        val userId = UUID.randomUUID()
        namesById[userId] = displayName
        return CoachClientEntity(
            id = UUID.randomUUID(),
            coachId = PAGE_COACH_ID,
            userId = userId,
            status = CoachClientStatus.ACTIVE,
            createdAt = NOW,
        )
    }
}
