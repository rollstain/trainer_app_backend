package app.trainer.backend.auth

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val USER_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000001")
private val SESSION_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000002")
private val OTHER_SESSION_ID: UUID = UUID.fromString("a0000000-0000-0000-0000-000000000003")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private const val ACCESS_TTL_MINUTES = 15L
private const val IDLE_DAYS = 90L
private const val ABSOLUTE_DAYS = 365L
private const val GRACE_SECONDS = 60L
private const val MAX_FAILED_ATTEMPTS = 5
private const val LOCK_MINUTES = 5L
private const val LOCK_MAX_MINUTES = 30L
private const val RESET_TTL_MINUTES = 60L
private const val RESEND_SECONDS = 120L
private const val INVITE_TTL_HOURS = 72L
private const val FIRST_TOKEN = "first-refresh-token"
private const val DEVICE = "Pixel 8"

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class SessionRotationTest {

    private val sessionRepository = mock(DeviceSessionRepository::class.java)
    private val tokenService = mock(TokenService::class.java)

    private val properties = AuthProperties(
        accessTokenTtlMinutes = ACCESS_TTL_MINUTES,
        refreshTokenIdleDays = IDLE_DAYS,
        refreshTokenAbsoluteDays = ABSOLUTE_DAYS,
        refreshRotationGraceSeconds = GRACE_SECONDS,
        inviteTtlHours = INVITE_TTL_HOURS,
        passwordMaxFailedAttempts = MAX_FAILED_ATTEMPTS,
        passwordLockMinutes = LOCK_MINUTES,
        passwordLockMaxMinutes = LOCK_MAX_MINUTES,
        passwordResetTtlMinutes = RESET_TTL_MINUTES,
        passwordResetResendSeconds = RESEND_SECONDS,
        jwtSecret = "secret",
        adminToken = "admin-token",
    )

    @Test
    fun `refreshing hands out a new token and remembers the old one`() {
        val session = session()
        val service = serviceAt(NOW)
        givenCurrentToken(FIRST_TOKEN, session)

        service.refresh(RefreshRequest(refreshToken = FIRST_TOKEN))

        assertEquals("hash-of-rotated", session.refreshTokenHash)
        assertEquals("hash-of-$FIRST_TOKEN", session.previousRefreshTokenHash)
        assertEquals(NOW, session.rotatedAt)
        assertEquals(NOW, session.lastSeenAt)
    }

    @Test
    fun `a lost answer lets the client retry with the same token`() {
        val session = session(
            previousHash = "hash-of-$FIRST_TOKEN",
            rotatedAt = NOW.minusSeconds(GRACE_SECONDS / 2),
        )
        val service = serviceAt(NOW)
        givenPreviousToken(FIRST_TOKEN, session)

        val tokens = service.refresh(RefreshRequest(refreshToken = FIRST_TOKEN))

        assertNotNull(tokens.refreshToken, "повтор в пределах окна выдаёт рабочую пару")
        assertEquals("hash-of-$FIRST_TOKEN", session.previousRefreshTokenHash, "окно не сдвигается")
        assertNull(session.revokedAt)
    }

    @Test
    fun `the same token after the window means it leaked and kills the session`() {
        val session = session(
            previousHash = "hash-of-$FIRST_TOKEN",
            rotatedAt = NOW.minusSeconds(GRACE_SECONDS * 2),
        )
        val service = serviceAt(NOW)
        givenPreviousToken(FIRST_TOKEN, session)

        val failure = assertFailsWith<ResponseStatusException> {
            service.refresh(RefreshRequest(refreshToken = FIRST_TOKEN))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, failure.statusCode)
        assertEquals(NOW, session.revokedAt, "сессия отозвана целиком")
    }

    @Test
    fun `an unknown token belongs to nobody`() {
        val service = serviceAt(NOW)
        `when`(tokenService.hash(FIRST_TOKEN)).thenReturn("hash-of-$FIRST_TOKEN")
        `when`(sessionRepository.findByRefreshTokenHash(anyNonNull())).thenReturn(null)
        `when`(sessionRepository.findByPreviousRefreshTokenHash(anyNonNull())).thenReturn(null)

        val failure = assertFailsWith<ResponseStatusException> {
            service.refresh(RefreshRequest(refreshToken = FIRST_TOKEN))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, failure.statusCode)
    }

    @Test
    fun `a session unused for too long expires`() {
        val session = session(lastSeenAt = NOW.minus(Duration.ofDays(IDLE_DAYS + 1)))
        val service = serviceAt(NOW)
        givenCurrentToken(FIRST_TOKEN, session)

        val failure = assertFailsWith<ResponseStatusException> {
            service.refresh(RefreshRequest(refreshToken = FIRST_TOKEN))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, failure.statusCode)
        assertEquals(NOW, session.revokedAt)
    }

    @Test
    fun `daily use keeps a session alive past the idle window`() {
        val session = session(
            createdAt = NOW.minus(Duration.ofDays(IDLE_DAYS * 2)),
            lastSeenAt = NOW.minus(Duration.ofDays(1)),
        )
        val service = serviceAt(NOW)
        givenCurrentToken(FIRST_TOKEN, session)

        service.refresh(RefreshRequest(refreshToken = FIRST_TOKEN))

        assertNull(session.revokedAt, "активная сессия не истекает по сроку от создания")
        assertEquals(NOW, session.lastSeenAt)
    }

    @Test
    fun `no session outlives the absolute limit`() {
        val session = session(
            createdAt = NOW.minus(Duration.ofDays(ABSOLUTE_DAYS + 1)),
            lastSeenAt = NOW.minus(Duration.ofHours(1)),
        )
        val service = serviceAt(NOW)
        givenCurrentToken(FIRST_TOKEN, session)

        val failure = assertFailsWith<ResponseStatusException> {
            service.refresh(RefreshRequest(refreshToken = FIRST_TOKEN))
        }

        assertEquals(HttpStatus.UNAUTHORIZED, failure.statusCode)
    }

    @Test
    fun `the device list marks the session asking for it`() {
        val service = serviceAt(NOW)
        `when`(sessionRepository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(USER_ID))
            .thenReturn(listOf(session(), session(id = OTHER_SESSION_ID)))

        val sessions = service.sessionsOf(userId = USER_ID, currentSessionId = SESSION_ID)

        assertEquals(listOf(true, false), sessions.map { it.isCurrent })
    }

    @Test
    fun `signing out everywhere spares the device doing it`() {
        val current = session()
        val other = session(id = OTHER_SESSION_ID)
        val service = serviceAt(NOW)
        `when`(sessionRepository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(USER_ID))
            .thenReturn(listOf(current, other))

        service.revokeOtherSessions(userId = USER_ID, currentSessionId = SESSION_ID)

        assertNull(current.revokedAt, "текущее устройство остаётся в системе")
        assertEquals(NOW, other.revokedAt)
    }

    @Test
    fun `someone else's session cannot be revoked`() {
        val service = serviceAt(NOW)
        `when`(sessionRepository.findById(OTHER_SESSION_ID))
            .thenReturn(java.util.Optional.of(session(id = OTHER_SESSION_ID, userId = UUID.randomUUID())))

        val failure = assertFailsWith<ResponseStatusException> {
            service.revokeSession(userId = USER_ID, sessionId = OTHER_SESSION_ID)
        }

        assertEquals(HttpStatus.NOT_FOUND, failure.statusCode)
    }

    private fun givenCurrentToken(token: String, session: DeviceSessionEntity) {
        `when`(tokenService.hash(token)).thenReturn("hash-of-$token")
        `when`(sessionRepository.findByRefreshTokenHash("hash-of-$token")).thenReturn(session)
    }

    private fun givenPreviousToken(token: String, session: DeviceSessionEntity) {
        `when`(tokenService.hash(token)).thenReturn("hash-of-$token")
        `when`(sessionRepository.findByRefreshTokenHash("hash-of-$token")).thenReturn(null)
        `when`(sessionRepository.findByPreviousRefreshTokenHash("hash-of-$token")).thenReturn(session)
    }

    private fun serviceAt(now: Instant): SessionService {
        `when`(tokenService.generateRefreshToken()).thenReturn("rotated")
        `when`(tokenService.hash("rotated")).thenReturn("hash-of-rotated")
        `when`(tokenService.issueAccessToken(anyNonNull(), anyNonNull()))
            .thenReturn(TokenService.AccessToken(value = "access", expiresAt = now.plusSeconds(900)))
        return SessionService(
            deviceSessionRepository = sessionRepository,
            tokenService = tokenService,
            properties = properties,
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    private fun session(
        id: UUID = SESSION_ID,
        userId: UUID = USER_ID,
        previousHash: String? = null,
        rotatedAt: Instant? = null,
        createdAt: Instant = NOW.minus(Duration.ofDays(10)),
        lastSeenAt: Instant = NOW.minus(Duration.ofHours(1)),
    ): DeviceSessionEntity = DeviceSessionEntity(
        id = id,
        userId = userId,
        refreshTokenHash = "hash-of-$FIRST_TOKEN",
        previousRefreshTokenHash = previousHash,
        rotatedAt = rotatedAt,
        deviceInfo = DEVICE,
        createdAt = createdAt,
        lastSeenAt = lastSeenAt,
        revokedAt = null,
    )
}
