package app.trainer.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "trainer.web")
data class WebClientProperties(
    val allowedOrigins: List<String>,
    val cookieSecure: Boolean,
    val cookieDomain: String,
)
