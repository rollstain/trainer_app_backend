package app.trainer.backend.notification

import app.trainer.backend.config.EXTRA_ROW_TO_DETECT_NEXT_PAGE
import app.trainer.backend.config.Page
import app.trainer.backend.config.PageCursor
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.config.encodeCursor
import app.trainer.backend.config.pageSizeOf
import app.trainer.backend.push.NotificationReason
import app.trainer.backend.push.PushTexts
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val NOTIFICATIONS_PER_PAGE = 30

private val ARGS_TYPE = object : TypeReference<List<String>>() {}
private val DATA_TYPE = object : TypeReference<Map<String, String>>() {}

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
    private val settingRepository: NotificationSettingRepository,
    private val pushTexts: PushTexts,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun page(userId: UUID, limit: Int?, after: String?, locale: Locale): Page<NotificationResponse> {
        val pageSize = pageSizeOf(limit) ?: NOTIFICATIONS_PER_PAGE
        val cursor = decodeCursor(after)
        val fetched = notificationRepository.findPage(
            userId = userId,
            beforeCreatedAt = cursor?.sortKey,
            beforeId = cursor?.id,
            pageSize = pageSize + EXTRA_ROW_TO_DETECT_NEXT_PAGE,
        )
        val shown = fetched.take(pageSize)
        val last = shown.lastOrNull()?.takeIf { fetched.size > pageSize }
        return Page(
            items = shown.map { toResponse(notification = it, locale = locale) },
            nextCursor = last?.let { encodeCursor(PageCursor(sortKey = it.createdAt.toString(), id = it.id)) },
        )
    }

    @Transactional(readOnly = true)
    fun unread(userId: UUID): UnreadNotificationsResponse =
        UnreadNotificationsResponse(count = notificationRepository.countByUserIdAndReadAtIsNull(userId))

    @Transactional
    fun readAll(userId: UUID) {
        notificationRepository.markAllRead(userId = userId, readAt = Instant.now(clock))
    }

    @Transactional(readOnly = true)
    fun settings(userId: UUID): List<NotificationSettingResponse> {
        val chosen = settingRepository.findByUserId(userId).associateBy { it.reason }
        return NotificationReason.entries.map { reason ->
            NotificationSettingResponse(reason = reason, pushEnabled = chosen[reason]?.pushEnabled ?: true)
        }
    }

    @Transactional
    fun updateSetting(
        userId: UUID,
        reason: NotificationReason,
        pushEnabled: Boolean,
    ): List<NotificationSettingResponse> {
        val known = settingRepository.findByUserIdAndReason(userId = userId, reason = reason)
        if (known == null) {
            settingRepository.save(
                NotificationSettingEntity(
                    id = UUID.randomUUID(),
                    userId = userId,
                    reason = reason,
                    pushEnabled = pushEnabled,
                )
            )
        } else {
            known.pushEnabled = pushEnabled
        }
        return settings(userId)
    }

    private fun toResponse(notification: NotificationEntity, locale: Locale): NotificationResponse {
        val rendered = pushTexts.render(
            text = notification.kind,
            args = objectMapper.readValue(notification.args, ARGS_TYPE),
            locale = locale,
        )
        return NotificationResponse(
            id = notification.id,
            kind = notification.kind,
            title = rendered.title,
            body = rendered.body,
            data = objectMapper.readValue(notification.data, DATA_TYPE),
            createdAt = notification.createdAt,
            isRead = notification.readAt != null,
        )
    }
}
