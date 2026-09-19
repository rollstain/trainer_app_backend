package app.trainer.backend.notification

import app.trainer.backend.config.decodeCursor
import app.trainer.backend.push.DEFAULT_PUSH_LOCALE
import app.trainer.backend.push.NotificationReason
import app.trainer.backend.push.PushText
import app.trainer.backend.push.PushTexts
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.context.support.ResourceBundleMessageSource

private val ANNA: UUID = UUID.fromString("94000000-0000-0000-0000-000000000001")
private val NOW: Instant = Instant.parse("2026-09-19T09:00:00Z")
private val EARLIER: Instant = Instant.parse("2026-09-18T09:00:00Z")
private const val PAGE_OF_ONE = 1

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class NotificationServiceTest {

    private val notificationRepository = mock(NotificationRepository::class.java)
    private val settingRepository = mock(NotificationSettingRepository::class.java)

    private val service = NotificationService(
        notificationRepository = notificationRepository,
        settingRepository = settingRepository,
        pushTexts = PushTexts(
            ResourceBundleMessageSource().apply {
                setBasename("messages")
                setDefaultEncoding("UTF-8")
                setFallbackToSystemLocale(false)
            }
        ),
        objectMapper = ObjectMapper(),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `history shows the same words the push had`() {
        val newest = notification(PushText.SLOT_CANCELLED, createdAt = NOW, readAt = null)
        val older = notification(PushText.WAITLIST_SLOT_FREED, createdAt = EARLIER, readAt = NOW)
        `when`(notificationRepository.findPage(ANNA, null, null, PAGE_OF_ONE + 1)).thenReturn(listOf(newest, older))

        val page = service.page(userId = ANNA, limit = PAGE_OF_ONE, after = null, locale = DEFAULT_PUSH_LOCALE)

        val shown = page.items.single()
        assertEquals("Занятие отменено", shown.title)
        assertEquals("Тренер отменил занятие 21.09 18:30. Другое время можно выбрать в записи.", shown.body)
        assertEquals(mapOf("slotId" to "slot"), shown.data)
        assertEquals(false, shown.isRead)
        assertEquals(newest.id, decodeCursor(page.nextCursor)?.id)
    }

    @Test
    fun `the last page has no cursor`() {
        `when`(notificationRepository.findPage(ANNA, null, null, PAGE_OF_ONE + 1))
            .thenReturn(listOf(notification(PushText.SLOT_CANCELLED, createdAt = NOW, readAt = NOW)))

        val page = service.page(userId = ANNA, limit = PAGE_OF_ONE, after = null, locale = DEFAULT_PUSH_LOCALE)

        assertNull(page.nextCursor)
        assertEquals(true, page.items.single().isRead)
    }

    @Test
    fun `every reason pushes until it is turned off`() {
        `when`(settingRepository.findByUserId(ANNA)).thenReturn(
            listOf(NotificationSettingEntity(UUID.randomUUID(), ANNA, NotificationReason.NEW_PROGRAMS, false))
        )

        val settings = service.settings(ANNA).associate { it.reason to it.pushEnabled }

        assertEquals(
            mapOf(
                NotificationReason.COACH_REPLIES to true,
                NotificationReason.SESSION_REMINDERS to true,
                NotificationReason.SCHEDULE_CHANGES to true,
                NotificationReason.NEW_PROGRAMS to false,
            ),
            settings,
        )
    }

    @Test
    fun `turning a reason off for the first time remembers it`() {
        `when`(settingRepository.save(anyNonNull<NotificationSettingEntity>()))
            .thenAnswer { it.arguments.first() as NotificationSettingEntity }

        service.updateSetting(userId = ANNA, reason = NotificationReason.SESSION_REMINDERS, pushEnabled = false)

        val saved = ArgumentCaptor.forClass(NotificationSettingEntity::class.java)
        verify(settingRepository).save(saved.capture())
        assertEquals(NotificationReason.SESSION_REMINDERS, saved.value.reason)
        assertEquals(false, saved.value.pushEnabled)
    }

    @Test
    fun `read all marks everything unread as read now`() {
        service.readAll(ANNA)

        verify(notificationRepository).markAllRead(ANNA, NOW)
    }

    private fun notification(kind: PushText, createdAt: Instant, readAt: Instant?) = NotificationEntity(
        id = UUID.randomUUID(),
        userId = ANNA,
        kind = kind,
        args = "[\"21.09 18:30\"]",
        data = "{\"slotId\":\"slot\"}",
        createdAt = createdAt,
        readAt = readAt,
    )
}
