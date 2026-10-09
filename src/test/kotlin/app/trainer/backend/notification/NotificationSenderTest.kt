package app.trainer.backend.notification

import app.trainer.backend.coach.CoachQuietHoursLookup
import app.trainer.backend.coach.QuietWindow
import app.trainer.backend.push.NotificationReason
import app.trainer.backend.push.PushChannel
import app.trainer.backend.push.PushDelivery
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushText
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import tools.jackson.databind.ObjectMapper

private val ANNA: UUID = UUID.fromString("93000000-0000-0000-0000-000000000001")
private val MAX: UUID = UUID.fromString("93000000-0000-0000-0000-000000000002")
private val NOW: Instant = Instant.parse("2026-09-19T09:00:00Z")
private val MORNING: Instant = Instant.parse("2026-09-19T10:00:00Z")
private val QUIET_STARTED: Instant = Instant.parse("2026-09-19T07:00:00Z")
private val QUIET_STARTS: LocalTime = LocalTime.of(10, 0)
private val QUIET_ENDS: LocalTime = LocalTime.of(13, 0)
private val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
private fun <T> capturedBy(captor: ArgumentCaptor<*>): T = captor.capture() as T

class NotificationSenderTest {

    private val delivery = mock(PushDelivery::class.java)
    private val notificationRepository = mock(NotificationRepository::class.java)
    private val settingRepository = mock(NotificationSettingRepository::class.java)
    private val quietHours = mock(CoachQuietHoursLookup::class.java)

    private val sender = NotificationSender(
        delivery = delivery,
        notificationRepository = notificationRepository,
        settingRepository = settingRepository,
        quietHours = quietHours,
        objectMapper = ObjectMapper(),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `every recipient keeps the event in history and gets the push`() {
        val cancelled = message(PushText.SLOT_CANCELLED)

        sender.send(userIds = listOf(ANNA, MAX, ANNA), message = cancelled)

        val kept = keptNotifications()
        assertEquals(listOf(ANNA, MAX), kept.map { it.userId })
        assertEquals("[\"21.09 18:30\"]", kept.first().args)
        assertEquals("{\"slotId\":\"slot\"}", kept.first().data)
        verify(delivery).send(listOf(ANNA, MAX), cancelled)
    }

    @Test
    fun `a muted reason still lands in history but skips the push`() {
        `when`(
            settingRepository.findByUserIdInAndReasonAndPushEnabledFalse(
                listOf(ANNA, MAX),
                NotificationReason.SESSION_REMINDERS,
            )
        ).thenReturn(
            listOf(NotificationSettingEntity(UUID.randomUUID(), MAX, NotificationReason.SESSION_REMINDERS, false))
        )
        val reminder = message(PushText.SESSION_SOON)

        sender.send(userIds = listOf(ANNA, MAX), message = reminder)

        assertEquals(listOf(ANNA, MAX), keptNotifications().map { it.userId })
        verify(delivery).send(listOf(ANNA), reminder)
    }

    @Test
    fun `nobody left to push means no push at all`() {
        `when`(
            settingRepository.findByUserIdInAndReasonAndPushEnabledFalse(
                listOf(ANNA),
                NotificationReason.SCHEDULE_CHANGES,
            )
        ).thenReturn(
            listOf(NotificationSettingEntity(UUID.randomUUID(), ANNA, NotificationReason.SCHEDULE_CHANGES, false))
        )

        sender.send(userIds = listOf(ANNA), message = message(PushText.WAITLIST_SLOT_FREED))

        verify(delivery, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a check-in waits for the daily digest instead of its own push`() {
        sender.send(userIds = listOf(ANNA), message = message(PushText.NEW_CHECK_IN))

        assertEquals(listOf(ANNA), keptNotifications().map { it.userId })
        verify(delivery, never()).send(anyNonNull(), anyNonNull())
        verifyNoInteractions(settingRepository)
    }

    @Test
    fun `the digest itself goes out and is not kept twice`() {
        val digest = message(PushText.CHECK_INS_WAITING)

        sender.send(userIds = listOf(ANNA), message = digest)

        verifyNoInteractions(notificationRepository)
        verify(delivery).send(listOf(ANNA), digest)
    }

    @Test
    fun `chat messages are delivered but not kept, the chat is their history`() {
        val chat = message(PushText.NEW_CHAT_MESSAGE)

        sender.send(userIds = listOf(ANNA), message = chat)

        verifyNoInteractions(notificationRepository)
        verify(delivery).send(listOf(ANNA), chat)
    }

    @Test
    fun `reminders without a reason cannot be muted`() {
        val diary = message(PushText.DIARY_IDLE)

        sender.send(userIds = listOf(ANNA), message = diary)

        verifyNoInteractions(settingRepository)
        verify(delivery).send(listOf(ANNA), diary)
    }

    @Test
    fun `a coach in quiet hours keeps the event till morning without a push`() {
        givenQuiet(ANNA)
        val booked = message(PushText.SLOT_BOOKED)

        sender.send(userIds = listOf(ANNA, MAX), message = booked)

        val kept = keptNotifications()
        val anna = kept.single { it.userId == ANNA }
        assertEquals(MORNING, anna.heldUntil)
        assertEquals(QUIET_STARTED, anna.quietFrom)
        assertEquals(MORNING, anna.quietUntil)
        assertNull(kept.single { it.userId == MAX }.heldUntil)
        assertNull(kept.single { it.userId == MAX }.quietUntil)
        verify(delivery).send(listOf(MAX), booked)
    }

    @Test
    fun `a cancel request reaches a coach in quiet hours at once`() {
        givenQuiet(ANNA)
        val request = message(PushText.CANCEL_REQUESTED)

        sender.send(userIds = listOf(ANNA), message = request)

        val kept = keptNotifications().single()
        assertNull(kept.heldUntil)
        assertEquals(MORNING, kept.quietUntil)
        verify(delivery).send(listOf(ANNA), request)
    }

    @Test
    fun `a muted reason is not saved for the morning`() {
        givenQuiet(ANNA)
        `when`(
            settingRepository.findByUserIdInAndReasonAndPushEnabledFalse(
                listOf(ANNA),
                NotificationReason.SLOT_BOOKINGS,
            )
        ).thenReturn(
            listOf(NotificationSettingEntity(UUID.randomUUID(), ANNA, NotificationReason.SLOT_BOOKINGS, false))
        )

        sender.send(userIds = listOf(ANNA), message = message(PushText.SLOT_BOOKED))

        assertNull(keptNotifications().single().heldUntil)
        verify(delivery, never()).send(anyNonNull(), anyNonNull())
    }

    @Test
    fun `chat stays silent in quiet hours`() {
        givenQuiet(ANNA)

        sender.send(userIds = listOf(ANNA), message = message(PushText.NEW_CHAT_MESSAGE))

        verify(delivery, never()).send(anyNonNull(), anyNonNull())
    }

    private fun givenQuiet(userId: UUID) {
        val quiet = mapOf(userId to QuietWindow(startsAt = QUIET_STARTS, endsAt = QUIET_ENDS, zone = MOSCOW))
        `when`(quietHours.windowsOf(listOf(userId))).thenReturn(quiet)
        `when`(quietHours.windowsOf(listOf(userId, MAX))).thenReturn(quiet)
    }

    private fun keptNotifications(): List<NotificationEntity> {
        val captor = ArgumentCaptor.forClass(List::class.java)
        verify(notificationRepository).saveAll(capturedBy<List<NotificationEntity>>(captor))
        @Suppress("UNCHECKED_CAST")
        return captor.value as List<NotificationEntity>
    }

    private fun message(text: PushText) = PushMessage(
        channel = PushChannel.SCHEDULE,
        text = text,
        args = listOf("21.09 18:30"),
        data = mapOf("slotId" to "slot"),
    )
}
