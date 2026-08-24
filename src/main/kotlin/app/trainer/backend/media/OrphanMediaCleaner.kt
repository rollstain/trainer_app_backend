package app.trainer.backend.media

import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val CLEANUP_INTERVAL_MS = 3_600_000L
private const val ORPHAN_LIFETIME_HOURS = 24L

@Component
class OrphanMediaCleaner(private val mediaFileService: MediaFileService) {

    private val logger = LoggerFactory.getLogger(OrphanMediaCleaner::class.java)

    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MS)
    fun removeOrphans() {
        val removed = mediaFileService.removeOrphans(olderThan = Duration.ofHours(ORPHAN_LIFETIME_HOURS))
        if (removed > 0) logger.info("Удалено непривязанных файлов: {}", removed)
    }
}
