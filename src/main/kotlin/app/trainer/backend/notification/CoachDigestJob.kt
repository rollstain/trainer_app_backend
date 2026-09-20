package app.trainer.backend.notification

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val DIGEST_CHECK_INTERVAL_MS = 3_600_000L

@Component
class CoachDigestJob(private val coachDigestService: CoachDigestService) {

    private val logger = LoggerFactory.getLogger(CoachDigestJob::class.java)

    @Scheduled(fixedDelay = DIGEST_CHECK_INTERVAL_MS)
    fun sendDailyDigests() {
        val sent = coachDigestService.sendDailyDigests()
        if (sent > 0) logger.info("Сводок тренерам отправлено: {}", sent)
    }
}
