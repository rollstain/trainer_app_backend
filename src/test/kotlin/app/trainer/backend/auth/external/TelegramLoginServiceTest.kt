package app.trainer.backend.auth.external

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val NOW: Instant = Instant.parse("2026-08-29T10:00:00Z")
private const val BOT_USERNAME = "trainer_login_bot"
private const val TELEGRAM_USER_ID = "44417"
private const val TELEGRAM_NAME = "Дмитрий"
private const val TELEGRAM_USERNAME = "d_rogov"
private const val LOGIN_TTL_MINUTES = 10L
private val COACH_USER_ID: java.util.UUID = java.util.UUID.fromString("7d1f0f2e-0000-0000-0000-000000000001")

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class TelegramLoginServiceTest {

    private val loginRepository = mock(TelegramLoginRepository::class.java)
    private val saved = mutableListOf<TelegramLoginEntity>()

    @BeforeEach
    fun rememberSavedLogins() {
        `when`(loginRepository.save(anyNonNull<TelegramLoginEntity>())).thenAnswer { invocation ->
            val entity = invocation.arguments.first() as TelegramLoginEntity
            saved.add(entity)
            entity
        }
    }

    private fun serviceAt(now: Instant, botUsername: String = BOT_USERNAME): TelegramLoginService {
        return TelegramLoginService(
            loginRepository = loginRepository,
            properties = TelegramProperties(botUsername = botUsername, botSecret = "unused-in-this-test"),
            clock = Clock.fixed(now, ZoneOffset.UTC),
        )
    }

    @Test
    fun `the deep link points at the bot and the claim token is never stored as is`() {
        val service = serviceAt(NOW)

        val started = service.start()

        val stored = saved.single()
        assertEquals("https://t.me/$BOT_USERNAME?start=${stored.startCode}", started.deepLink)
        assertTrue(started.claimToken.isNotBlank())
        assertFalse(
            stored.claimTokenHash == started.claimToken,
            "в базе должен лежать хеш, а не сам токен",
        )
    }

    @Test
    fun `an unconfirmed login is not a session yet`() {
        val service = serviceAt(NOW)
        val started = service.start()
        givenStored(saved.single())

        val refused = assertThrows<ResponseStatusException> { service.consumeConfirmed(started.claimToken) }

        assertEquals(HttpStatus.CONFLICT, refused.statusCode, "ждём подтверждения, а не отказ")
    }

    @Test
    fun `a confirmed login turns into an identity exactly once`() {
        val service = serviceAt(NOW)
        val started = service.start()
        val login = saved.single()
        givenStored(login)
        `when`(loginRepository.findByStartCode(login.startCode)).thenReturn(login)

        assertNotNull(
            service.confirm(
                startCode = login.startCode,
                telegramUserId = TELEGRAM_USER_ID,
                telegramDisplayName = TELEGRAM_NAME,
                telegramUsername = TELEGRAM_USERNAME,
            )
        )
        val identity = service.consumeConfirmed(started.claimToken)

        assertEquals(ExternalProvider.TELEGRAM, identity.provider)
        assertEquals(TELEGRAM_USER_ID, identity.subject)
        assertEquals(TELEGRAM_NAME, identity.displayName)

        val reused = assertThrows<ResponseStatusException> { service.consumeConfirmed(started.claimToken) }
        assertEquals(HttpStatus.GONE, reused.statusCode, "ссылку нельзя разменять дважды")
    }

    @Test
    fun `a login left for the night is dead`() {
        val service = serviceAt(NOW)
        val started = service.start()
        val login = saved.single()
        givenStored(login)
        `when`(loginRepository.findByStartCode(login.startCode)).thenReturn(login)

        val late = serviceAt(NOW.plus(LOGIN_TTL_MINUTES + 1, ChronoUnit.MINUTES))

        assertNull(
            late.confirm(
                startCode = login.startCode,
                telegramUserId = TELEGRAM_USER_ID,
                telegramDisplayName = TELEGRAM_NAME,
                telegramUsername = TELEGRAM_USERNAME,
            ),
            "просроченную ссылку бот подтвердить не может",
        )
        val refused = assertThrows<ResponseStatusException> { late.consumeConfirmed(started.claimToken) }
        assertEquals(HttpStatus.GONE, refused.statusCode)
    }

    @Test
    fun `without a bot name the login is not offered at all`() {
        val service = serviceAt(NOW, botUsername = "")

        val refused = assertThrows<ResponseStatusException> { service.start() }

        assertEquals(HttpStatus.NOT_IMPLEMENTED, refused.statusCode)
    }

    private fun givenStored(login: TelegramLoginEntity) {
        `when`(loginRepository.findByClaimTokenHash(login.claimTokenHash)).thenReturn(login)
    }

    @Test
    fun `a claim link binds telegram to the coach instead of opening a session`() {
        val service = serviceAt(NOW)
        service.startClaim(targetUserId = COACH_USER_ID)
        val login = saved.single()
        `when`(loginRepository.findByStartCode(login.startCode)).thenReturn(login)

        val confirmed = service.confirm(
            startCode = login.startCode,
            telegramUserId = TELEGRAM_USER_ID,
            telegramDisplayName = TELEGRAM_NAME,
            telegramUsername = TELEGRAM_USERNAME,
        )

        assertNotNull(confirmed)
        assertEquals(COACH_USER_ID, confirmed.targetUserId)
        assertEquals(TELEGRAM_USER_ID, confirmed.identity?.subject)
        assertNull(
            service.confirm(
                startCode = login.startCode,
                telegramUserId = TELEGRAM_USER_ID,
                telegramDisplayName = TELEGRAM_NAME,
                telegramUsername = TELEGRAM_USERNAME,
            ),
            "ссылку привязки нельзя использовать дважды",
        )
    }
}
