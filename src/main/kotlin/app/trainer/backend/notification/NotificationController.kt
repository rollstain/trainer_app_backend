package app.trainer.backend.notification

import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.config.pageResponse
import app.trainer.backend.push.NotificationReason
import java.util.Locale
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class NotificationController(private val notificationService: NotificationService) {

    @GetMapping("/notifications")
    fun notifications(
        @CurrentUserId userId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) after: String?,
        locale: Locale,
    ): ResponseEntity<List<NotificationResponse>> =
        pageResponse(notificationService.page(userId = userId, limit = limit, after = after, locale = locale))

    @GetMapping("/notifications/unread")
    fun unread(@CurrentUserId userId: UUID): UnreadNotificationsResponse = notificationService.unread(userId)

    @PostMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun readAll(@CurrentUserId userId: UUID) {
        notificationService.readAll(userId)
    }

    @GetMapping("/me/notification-settings")
    fun settings(@CurrentUserId userId: UUID): List<NotificationSettingResponse> =
        notificationService.settings(userId)

    @PutMapping("/me/notification-settings/{reason}")
    fun updateSetting(
        @CurrentUserId userId: UUID,
        @PathVariable reason: NotificationReason,
        @RequestBody request: UpdateNotificationSettingRequest,
    ): List<NotificationSettingResponse> = notificationService.updateSetting(
        userId = userId,
        reason = reason,
        pushEnabled = request.pushEnabled,
    )
}
