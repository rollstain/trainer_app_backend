package app.trainer.backend.config

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner

private const val FIRST_ORIGIN = "https://app.lyashukfit.ru"
private const val SECOND_ORIGIN = "https://staging.lyashukfit.ru"

@EnableConfigurationProperties(WebClientProperties::class)
private class WebClientPropertiesHolder

class WebClientPropertiesBindingTest {

    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of())
        .withUserConfiguration(WebClientPropertiesHolder::class.java)

    @Test
    fun `прод без веб-клиента поднимается с пустым списком origin`() {
        runner
            .withPropertyValues(
                "trainer.web.allowed-origins=",
                "trainer.web.cookie-secure=true",
                "trainer.web.cookie-domain=",
            )
            .run { context ->
                val properties = context.getBean(WebClientProperties::class.java)
                assertTrue(properties.allowedOrigins.isEmpty())
                assertTrue(properties.cookieSecure)
                assertEquals("", properties.cookieDomain)
            }
    }

    @Test
    fun `несколько origin перечисляются через запятую`() {
        runner
            .withPropertyValues(
                "trainer.web.allowed-origins=$FIRST_ORIGIN,$SECOND_ORIGIN",
                "trainer.web.cookie-secure=false",
                "trainer.web.cookie-domain=lyashukfit.ru",
            )
            .run { context ->
                val properties = context.getBean(WebClientProperties::class.java)
                assertEquals(listOf(FIRST_ORIGIN, SECOND_ORIGIN), properties.allowedOrigins)
            }
    }
}
