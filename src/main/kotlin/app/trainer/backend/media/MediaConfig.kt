package app.trainer.backend.media

import java.time.Duration
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private const val CONFIGURED_ENDPOINT_EXPRESSION = "'\${trainer.media.endpoint:}'.trim().length() > 0"

@Configuration
@EnableConfigurationProperties(MediaProperties::class)
class MediaConfig {

    @Bean
    @ConditionalOnExpression(CONFIGURED_ENDPOINT_EXPRESSION)
    fun s3MediaStorage(properties: MediaProperties): MediaStorage = S3MediaStorage(properties)

    @Bean
    @ConditionalOnMissingBean(MediaStorage::class)
    fun unavailableMediaStorage(): MediaStorage = UnavailableMediaStorage()
}

class UnavailableMediaStorage : MediaStorage {

    override fun presignUpload(
        storageKey: String,
        contentType: String,
        lifetime: Duration,
    ): PresignedUpload = unavailable()

    override fun presignDownload(storageKey: String, lifetime: Duration): String = unavailable()

    override fun head(storageKey: String): StoredObject = unavailable()

    override fun delete(storageKey: String) = unavailable()

    private fun unavailable(): Nothing {
        throw ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Хранилище файлов не настроено: задайте trainer.media.endpoint",
        )
    }
}
