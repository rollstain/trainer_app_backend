package app.trainer.backend.chat

import app.trainer.backend.auth.AuthProperties
import app.trainer.backend.auth.TokenService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.security.oauth2.jwt.JwtEncoder

private val USER_ID: UUID = UUID.fromString("b0000000-0000-0000-0000-000000000001")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private val AFTER_TICKET_DIED: Duration = Duration.ofSeconds(31)

private val AUTH_PROPERTIES = AuthProperties(
    accessTokenTtlMinutes = 15L,
    refreshTokenIdleDays = 90L,
    refreshTokenAbsoluteDays = 365L,
    refreshRotationGraceSeconds = 60L,
    inviteTtlHours = 72L,
    passwordMaxFailedAttempts = 5,
    passwordLockMinutes = 5L,
    passwordLockMaxMinutes = 30L,
    passwordResetTtlMinutes = 60L,
    passwordResetResendSeconds = 120L,
    emailConfirmTtlHours = 72L,
    emailConfirmResendSeconds = 120L,
    jwtSecret = "secret",
    adminToken = "admin-token",
)

private class SteppingClock(private var current: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = current
    fun step(duration: Duration) {
        current = current.plus(duration)
    }
}

class WebSocketTicketTest {

    private val clock = SteppingClock(NOW)
    private val tokenService = TokenService(
        jwtEncoder = mock(JwtEncoder::class.java),
        properties = AUTH_PROPERTIES,
        clock = clock,
    )
    private val service = WebSocketTicketService(tokenService = tokenService, clock = clock)

    @Test
    fun `свежий тикет открывает сокет своему владельцу`() {
        val issued = service.issue(USER_ID)

        assertEquals(USER_ID, service.consume(issued.ticket))
    }

    @Test
    fun `тикет гасится после первого использования`() {
        val issued = service.issue(USER_ID)
        service.consume(issued.ticket)

        assertNull(service.consume(issued.ticket))
    }

    @Test
    fun `просроченный тикет не пускает`() {
        val issued = service.issue(USER_ID)
        clock.step(AFTER_TICKET_DIED)

        assertNull(service.consume(issued.ticket))
    }

    @Test
    fun `выдуманный тикет не пускает`() {
        assertNull(service.consume("подделка"))
    }
}
