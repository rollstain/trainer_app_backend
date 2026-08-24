package app.trainer.backend.media

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "trainer.media")
data class MediaProperties(
    val bucket: String,
    val endpoint: String,
    val region: String,
    val accessKey: String,
    val secretKey: String,
    val uploadUrlLifetimeMinutes: Long,
    val downloadUrlLifetimeMinutes: Long,
    val maxFileSizeBytes: Long,
    val allowedContentTypes: List<String>,
)
