package app.trainer.backend.auth

import app.trainer.backend.config.WebClientProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

const val REFRESH_TOKEN_COOKIE_NAME = "trainer_refresh"

private const val REFRESH_TOKEN_COOKIE_PATH = "/auth"
private const val CROSS_SITE_REQUESTS_FORBIDDEN = "Strict"

@Component
class RefreshTokenCookie(
    private val authProperties: AuthProperties,
    private val webClientProperties: WebClientProperties,
) {

    fun write(response: HttpServletResponse, refreshToken: String) {
        val livesAsLongAsSession = Duration.ofDays(authProperties.refreshTokenIdleDays)
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshToken, livesAsLongAsSession).toString())
    }

    fun clear(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString())
    }

    fun read(request: HttpServletRequest): String? {
        return request.cookies
            ?.firstOrNull { it.name == REFRESH_TOKEN_COOKIE_NAME }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

    private fun cookie(value: String, maxAge: Duration): ResponseCookie {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, value)
            .httpOnly(true)
            .secure(webClientProperties.cookieSecure)
            .sameSite(CROSS_SITE_REQUESTS_FORBIDDEN)
            .path(REFRESH_TOKEN_COOKIE_PATH)
            .maxAge(maxAge)
            .build()
    }
}
