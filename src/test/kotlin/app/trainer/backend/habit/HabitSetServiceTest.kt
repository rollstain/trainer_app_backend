package app.trainer.backend.habit

import app.trainer.backend.coach.CoachClientEntity
import app.trainer.backend.coach.CoachClientRepository
import app.trainer.backend.coach.CoachClientStatus
import app.trainer.backend.coach.CoachEntity
import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val COACH_USER_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000002")
private val OTHER_COACH_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000003")
private val CLIENT_USER_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000004")
private val SET_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000005")
private val SECOND_CLIENT_USER_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000006")
private val NOW: Instant = Instant.parse("2026-10-09T09:00:00Z")
private const val CANCELLATION_WINDOW_HOURS = 12
private const val REMINDER_HOUR = 10
private const val HABITS_ADDED_FROM_SET = 2
private const val SET_TITLE = "Утро"
private const val ELENA = "Елена Литвинова"
private const val ANNA = "Анна Ковалёва"
private const val WATER = "Вода 2 л"
private const val SLEEP = "Сон 8 часов"
private const val STEPS = "10 000 шагов"

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
private fun <T> capturedBy(captor: ArgumentCaptor<*>): T = captor.capture() as T

class HabitSetServiceTest {

    private val setRepository = mock(HabitSetRepository::class.java)
    private val itemRepository = mock(HabitSetItemRepository::class.java)
    private val habitRepository = mock(HabitRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val coachClientRepository = mock(CoachClientRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)

    private val service = HabitSetService(
        setRepository = setRepository,
        itemRepository = itemRepository,
        habitRepository = habitRepository,
        coachRepository = coachRepository,
        coachClientRepository = coachClientRepository,
        userRepository = userRepository,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `a set keeps its habits in the order the coach gave them`() {
        givenCoach()
        `when`(setRepository.save(anyNonNull<HabitSetEntity>())).thenAnswer { it.arguments[0] }
        var stored = emptyList<HabitSetItemEntity>()
        `when`(itemRepository.saveAll(anyNonNull<Iterable<HabitSetItemEntity>>())).thenAnswer { invocation ->
            stored = (invocation.arguments[0] as Iterable<*>).map { it as HabitSetItemEntity }
            stored
        }
        `when`(itemRepository.findBySetIdInOrderByPositionAsc(anyNonNull())).thenAnswer { stored }
        val saved = ArgumentCaptor.forClass(Iterable::class.java)

        val created = service.create(
            coachUserId = COACH_USER_ID,
            request = HabitSetRequest(title = " $SET_TITLE ", habits = listOf(WATER, " $STEPS ")),
        )

        verify(itemRepository).saveAll(capturedBy<Iterable<HabitSetItemEntity>>(saved))
        val items = saved.value.map { it as HabitSetItemEntity }
        assertEquals(listOf(0, 1), items.map { it.position })
        assertEquals(listOf(WATER, STEPS), items.map { it.title })
        assertEquals(SET_TITLE, created.title)
        assertEquals(listOf(WATER, STEPS), created.habits)
        assertEquals(0, created.assignedClientsCount)
    }

    @Test
    fun `the same habit twice in a set is turned away`() {
        givenCoach()

        val failure = assertFailsWith<ResponseStatusException> {
            service.create(
                coachUserId = COACH_USER_ID,
                request = HabitSetRequest(title = SET_TITLE, habits = listOf(WATER, " ${WATER.uppercase()} ")),
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, failure.statusCode)
        verify(setRepository, never()).save(anyNonNull<HabitSetEntity>())
    }

    @Test
    fun `editing a set replaces its habits and leaves clients' habits alone`() {
        givenCoach()
        givenSet(coachId = COACH_ID)
        givenItems(SLEEP)

        val edited = service.update(
            coachUserId = COACH_USER_ID,
            setId = SET_ID,
            request = HabitSetRequest(title = SET_TITLE, habits = listOf(SLEEP)),
        )

        verify(itemRepository).deleteBySetId(SET_ID)
        verify(habitRepository, never()).save(anyNonNull<HabitEntity>())
        assertEquals(listOf(SLEEP), edited.habits)
        assertEquals(NOW, edited.updatedAt)
    }

    @Test
    fun `another coach's set is not theirs to edit`() {
        givenCoach()
        givenSet(coachId = OTHER_COACH_ID)

        val failure = assertFailsWith<ResponseStatusException> {
            service.update(
                coachUserId = COACH_USER_ID,
                setId = SET_ID,
                request = HabitSetRequest(title = SET_TITLE, habits = listOf(SLEEP)),
            )
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
        verify(itemRepository, never()).deleteBySetId(anyNonNull())
    }

    @Test
    fun `the list tells who got each set`() {
        givenCoach()
        `when`(setRepository.findByCoachIdOrderByCreatedAtAsc(COACH_ID)).thenReturn(listOf(set(coachId = COACH_ID)))
        givenItems(WATER)
        `when`(habitRepository.clientsOfSets(listOf(SET_ID))).thenReturn(
            listOf(setClient(CLIENT_USER_ID), setClient(SECOND_CLIENT_USER_ID))
        )
        `when`(userRepository.findAllById(listOf(CLIENT_USER_ID, SECOND_CLIENT_USER_ID))).thenReturn(
            listOf(user(CLIENT_USER_ID, ELENA), user(SECOND_CLIENT_USER_ID, ANNA))
        )

        val listed = service.setsOfCoach(coachUserId = COACH_USER_ID).single()

        assertEquals(2, listed.assignedClientsCount)
        assertEquals(listOf(ANNA, ELENA), listed.assignedClientNames)
        assertEquals(NOW, listed.createdAt)
    }

    @Test
    fun `assigning a set adds only the habits the client does not have yet`() {
        givenCoach()
        givenActiveClient()
        givenSet(coachId = COACH_ID)
        givenItems(WATER, SLEEP, STEPS)
        `when`(habitRepository.findByClientUserIdAndArchivedAtIsNullOrderByCreatedAtAsc(CLIENT_USER_ID))
            .thenReturn(listOf(habit(WATER.lowercase())))
        `when`(habitRepository.save(anyNonNull<HabitEntity>())).thenAnswer { it.arguments[0] }
        val saved = ArgumentCaptor.forClass(HabitEntity::class.java)

        val created = service.assign(
            coachUserId = COACH_USER_ID,
            clientUserId = CLIENT_USER_ID,
            setId = SET_ID,
            request = null,
        )

        assertEquals(listOf(SLEEP, STEPS), created.map { it.title })
        verify(habitRepository, times(HABITS_ADDED_FROM_SET)).save(capturedBy<HabitEntity>(saved))
        assertEquals(listOf(SET_ID, SET_ID), saved.allValues.map { it.habitSetId })
        assertEquals(listOf(COACH_ID, COACH_ID), saved.allValues.map { it.coachId })
    }

    @Test
    fun `the coach can leave part of a set unticked`() {
        givenCoach()
        givenActiveClient()
        givenSet(coachId = COACH_ID)
        givenItems(WATER, SLEEP, STEPS)
        `when`(habitRepository.save(anyNonNull<HabitEntity>())).thenAnswer { it.arguments[0] }

        val created = service.assign(
            coachUserId = COACH_USER_ID,
            clientUserId = CLIENT_USER_ID,
            setId = SET_ID,
            request = AssignHabitSetRequest(habits = listOf(STEPS.uppercase())),
        )

        assertEquals(listOf(STEPS), created.map { it.title })
    }

    @Test
    fun `a habit outside the set cannot be ticked`() {
        givenCoach()
        givenActiveClient()
        givenSet(coachId = COACH_ID)
        givenItems(WATER)

        val failure = assertFailsWith<ResponseStatusException> {
            service.assign(
                coachUserId = COACH_USER_ID,
                clientUserId = CLIENT_USER_ID,
                setId = SET_ID,
                request = AssignHabitSetRequest(habits = listOf(SLEEP)),
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, failure.statusCode)
        verify(habitRepository, never()).save(anyNonNull<HabitEntity>())
    }

    @Test
    fun `a set goes only to the coach's own client`() {
        givenCoach()
        givenSet(coachId = COACH_ID)

        val failure = assertFailsWith<ResponseStatusException> {
            service.assign(
                coachUserId = COACH_USER_ID,
                clientUserId = CLIENT_USER_ID,
                setId = SET_ID,
                request = null,
            )
        }

        assertEquals(HttpStatus.FORBIDDEN, failure.statusCode)
    }

    private fun givenCoach() {
        `when`(coachRepository.findByUserId(COACH_USER_ID)).thenReturn(coach())
    }

    private fun givenActiveClient() {
        `when`(coachClientRepository.findByCoachIdAndUserId(COACH_ID, CLIENT_USER_ID)).thenReturn(
            CoachClientEntity(
                id = UUID.randomUUID(),
                coachId = COACH_ID,
                userId = CLIENT_USER_ID,
                status = CoachClientStatus.ACTIVE,
                createdAt = NOW,
            )
        )
    }

    private fun givenSet(coachId: UUID) {
        `when`(setRepository.findById(SET_ID)).thenReturn(Optional.of(set(coachId = coachId)))
    }

    private fun givenItems(vararg titles: String) {
        `when`(itemRepository.findBySetIdInOrderByPositionAsc(listOf(SET_ID))).thenReturn(
            titles.mapIndexed { position, title ->
                HabitSetItemEntity(id = UUID.randomUUID(), setId = SET_ID, position = position, title = title)
            }
        )
    }

    private fun set(coachId: UUID): HabitSetEntity =
        HabitSetEntity(id = SET_ID, coachId = coachId, title = SET_TITLE, createdAt = NOW, updatedAt = null)

    private fun setClient(clientId: UUID): HabitSetClient = object : HabitSetClient {
        override val setId: UUID = SET_ID
        override val clientUserId: UUID = clientId
    }

    private fun user(id: UUID, name: String): UserEntity = UserEntity(
        id = id,
        displayName = name,
        phone = null,
        email = null,
        login = null,
        isOwner = false,
        createdAt = NOW,
    )

    private fun habit(title: String): HabitEntity = HabitEntity(
        id = UUID.randomUUID(),
        coachId = COACH_ID,
        clientUserId = CLIENT_USER_ID,
        title = title,
        createdAt = NOW,
        archivedAt = null,
        habitSetId = null,
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
