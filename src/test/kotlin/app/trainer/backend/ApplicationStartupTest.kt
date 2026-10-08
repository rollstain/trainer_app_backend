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
    properties = ["trainer.auth.jwt-secret=$STARTUP_JWT_SECRET"],
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
    fun `health and the API description answer`() {
        val client = RestClient.create("http://localhost:$port")

        val health = client.get().uri("/actuator/health").retrieve().toEntity(String::class.java)
        val apiDocs = client.get().uri("/v3/api-docs").retrieve().toEntity(String::class.java)

        assertEquals(HttpStatus.OK, health.statusCode)
        assertTrue(health.body.orEmpty().contains("UP"), health.body)
        assertEquals(HttpStatus.OK, apiDocs.statusCode)
    }

    @Test
    fun `an answer over HTTP leaves absent values out`() {
        val client = RestClient.create("http://localhost:$port")

        val (status, body) = client.get().uri("/auth/invites/$UNKNOWN_INVITE_CODE")
            .exchange { _, response -> response.statusCode to response.bodyTo(String::class.java).orEmpty() }

        assertEquals(HttpStatus.NOT_FOUND, status)
        assertTrue(body.contains("\"message\""), body)
        assertFalse(body.contains("retryAfterSeconds"), body)
    }

    companion object {

        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer(PRODUCTION_POSTGRES_IMAGE)
    }
}
