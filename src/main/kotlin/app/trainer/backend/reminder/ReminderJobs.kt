package app.trainer.backend.reminder

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val SESSION_CHECK_INTERVAL_MS = 300_000L
private const val ENGAGEMENT_CHECK_INTERVAL_MS = 3_600_000L

@Component
class ReminderJobs(private val reminderService: ReminderService) {

    private val logger = LoggerFactory.getLogger(ReminderJobs::class.java)

    @Scheduled(fixedDelay = SESSION_CHECK_INTERVAL_MS)
    fun remindAboutSessions() {
        val sent = reminderService.remindAboutSessions()
        if (sent > 0) logger.info("Напоминаний о тренировке отправлено: {}", sent)
    }

    @Scheduled(fixedDelay = ENGAGEMENT_CHECK_INTERVAL_MS)
    fun remindAboutEngagement() {
        val sent = reminderService.remindAboutEngagement()
        if (sent > 0) logger.info("Напоминаний о дневнике и замерах отправлено: {}", sent)
    }
}
