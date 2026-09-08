package app.trainer.backend.auth

import app.trainer.backend.config.WebClientProperties
import jakarta.servlet.http.Cookie
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.web.csrf.CookieCsrfTokenRepository

private const val ACCESS_TOKEN = "access-token"
private const val REFRESH_TOKEN = "refresh-token"
private const val WEB_ORIGIN = "https://app.lyashukfit.ru"
private const val COOKIE_DOMAIN = "lyashukfit.ru"
private const val CSRF_COOKIE_NAME = "XSRF-TOKEN"
private const val EXISTING_CSRF_TOKEN = "csrf-from-previous-response"
private val EXPIRES_AT: Instant = Instant.parse("2026-03-02T09:15:00Z")

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

class SessionCookieTransportTest {

    private val webClientProperties = WebClientProperties(
        allowedOrigins = listOf(WEB_ORIGIN),
        cookieSecure = true,
        cookieDomain = COOKIE_DOMAIN,
    )
    private val refreshTokenCookie = RefreshTokenCookie(
        authProperties = AUTH_PROPERTIES,
        webClientProperties = webClientProperties,
    )
    private val csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse().apply {
        setCookieCustomizer { cookie -> cookie.secure(true).domain(COOKIE_DOMAIN) }
    }
    private val responder = AuthTokensResponder(
        refreshTokenCookie = refreshTokenCookie,
        csrfCookie = CsrfCookie(csrfTokenRepository),
    )

    private val tokens = AuthTokensResponse(
        accessToken = ACCESS_TOKEN,
        refreshToken = REFRESH_TOKEN,
        accessTokenExpiresAt = EXPIRES_AT,
    )

    @Test
    fun `браузеру refresh уходит только в защищённой cookie`() {
        val request = MockHttpServletRequest()
        request.addHeader(SESSION_TRANSPORT_HEADER, "cookie")
        val response = MockHttpServletResponse()

        val body = responder.respond(tokens = tokens, request = request, response = response)

        assertNull(body.refreshToken)
        assertEquals(ACCESS_TOKEN, body.accessToken)
        val setCookie = response.getHeader(HttpHeaders.SET_COOKIE).orEmpty()
        assertTrue(setCookie.contains("$REFRESH_TOKEN_COOKIE_NAME=$REFRESH_TOKEN"))
        assertTrue(setCookie.contains("HttpOnly"))
        assertTrue(setCookie.contains("Secure"))
        assertTrue(setCookie.contains("SameSite=Strict"))
        assertTrue(setCookie.contains("Path=/auth"))
    }

    @Test
    fun `мобильный клиент получает refresh в теле и без cookie`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        val body = responder.respond(tokens = tokens, request = request, response = response)

        assertEquals(REFRESH_TOKEN, body.refreshToken)
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `браузер получает CSRF-токен вместе с сессией, а не после первого отказа`() {
        val request = MockHttpServletRequest()
        request.addHeader(SESSION_TRANSPORT_HEADER, "cookie")
        val response = MockHttpServletResponse()

        responder.respond(tokens = tokens, request = request, response = response)

        val csrfCookie = response.getHeaders(HttpHeaders.SET_COOKIE)
            .singleOrNull { it.startsWith("$CSRF_COOKIE_NAME=") }
        assertNotNull(csrfCookie)
        assertTrue(csrfCookie.contains("Domain=$COOKIE_DOMAIN"))
        assertFalse(csrfCookie.startsWith("$CSRF_COOKIE_NAME=;"))
    }

    @Test
    fun `мобильному клиенту CSRF-токен не выдаётся`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        responder.respond(tokens = tokens, request = request, response = response)

        assertTrue(response.getHeaders(HttpHeaders.SET_COOKIE).isEmpty())
    }

    @Test
    fun `выданный CSRF-токен переиспользуется, а не выпускается заново`() {
        val request = MockHttpServletRequest()
        request.addHeader(SESSION_TRANSPORT_HEADER, "cookie")
        request.setCookies(Cookie(CSRF_COOKIE_NAME, EXISTING_CSRF_TOKEN))
        val response = MockHttpServletResponse()

        responder.respond(tokens = tokens, request = request, response = response)

        val csrfCookie = response.getHeaders(HttpHeaders.SET_COOKIE)
            .single { it.startsWith("$CSRF_COOKIE_NAME=") }
        assertTrue(csrfCookie.startsWith("$CSRF_COOKIE_NAME=$EXISTING_CSRF_TOKEN"))
    }

    @Test
    fun `refresh читается из cookie запроса`() {
        val request = MockHttpServletRequest()
        request.setCookies(Cookie(REFRESH_TOKEN_COOKIE_NAME, REFRESH_TOKEN))

        assertEquals(REFRESH_TOKEN, refreshTokenCookie.read(request))
    }

    @Test
    fun `пустая cookie не считается токеном`() {
        val request = MockHttpServletRequest()
        request.setCookies(Cookie(REFRESH_TOKEN_COOKIE_NAME, ""))

        assertNull(refreshTokenCookie.read(request))
    }

    @Test
    fun `выход гасит cookie`() {
        val response = MockHttpServletResponse()

        refreshTokenCookie.clear(response)

        val setCookie = response.getHeader(HttpHeaders.SET_COOKIE).orEmpty()
        assertTrue(setCookie.contains("Max-Age=0"))
    }
}
