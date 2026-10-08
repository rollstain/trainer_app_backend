package app.trainer.backend

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.RestClient
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

private const val PRODUCTION_POSTGRES_IMAGE = "postgres:16-alpine"
private const val STARTUP_JWT_SECRET = "startup-test-secret-not-used-anywhere-else"
private const val UNKNOWN_INVITE_CODE = "no-such-invite"

@Tag("startup")
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "trainer.auth.jwt-secret=$STARTUP_JWT_SECRET",
        "management.endpoint.health.show-details=always",
        "management.health.mail.enabled=false",
    ],
)
class ApplicationStartupTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var flyway: Flyway

    @Test
    fun `the service starts on an empty database with every migration applied`() {
        val migrations = flyway.info()

        assertNotNull(migrations.current(), "Flyway не применил ни одной миграции")
        assertTrue(migrations.pending().isEmpty(), "остались неприменённые миграции")
    }

    @Test
    fun `health answers up`() {
        val (status, body) = get("/actuator/health")

        assertEquals(HttpStatus.OK, status, body)
    }

    @Test
    fun `the API description answers`() {
        val (status, body) = get("/v3/api-docs")

        assertEquals(HttpStatus.OK, status, body)
    }

    @Test
    fun `an answer over HTTP leaves absent values out`() {
        val (status, body) = get("/auth/invites/$UNKNOWN_INVITE_CODE")

        assertEquals(HttpStatus.NOT_FOUND, status)
        assertTrue(body.contains("\"message\""), body)
        assertFalse(body.contains("retryAfterSeconds"), body)
    }

    private fun get(path: String): Pair<HttpStatusCode, String> =
        RestClient.create("http://localhost:$port").get().uri(path)
            .exchange { _, response -> response.statusCode to response.bodyTo(String::class.java).orEmpty() }

    companion object {

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(PRODUCTION_POSTGRES_IMAGE)
    }
}
