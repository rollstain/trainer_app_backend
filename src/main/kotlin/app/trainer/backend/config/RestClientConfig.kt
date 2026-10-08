package app.trainer.backend.config

import java.time.Duration
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

private val PROVIDER_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
private val PROVIDER_READ_TIMEOUT: Duration = Duration.ofSeconds(10)

@Configuration
class RestClientConfig {

    @Bean
    fun restClient(): RestClient {
        val settings = HttpClientSettings.defaults()
            .withConnectTimeout(PROVIDER_CONNECT_TIMEOUT)
            .withReadTimeout(PROVIDER_READ_TIMEOUT)
        return RestClient.builder()
            .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
            .build()
    }
}
