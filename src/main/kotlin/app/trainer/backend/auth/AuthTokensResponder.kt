package app.trainer.backend.auth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component

const val SESSION_TRANSPORT_HEADER = "X-Session-Transport"

private const val COOKIE_TRANSPORT = "cookie"

@Component
class AuthTokensResponder(private val refreshTokenCookie: RefreshTokenCookie) {

    fun respond(
        tokens: AuthTokensResponse,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): AuthTokensResponse {
        if (!cookieTransportRequested(request)) return tokens
        val refreshToken = checkNotNull(tokens.refreshToken) { "Сессия открыта без refresh-токена" }
        refreshTokenCookie.write(response = response, refreshToken = refreshToken)
        return tokens.copy(refreshToken = null)
    }

    private fun cookieTransportRequested(request: HttpServletRequest): Boolean {
        return request.getHeader(SESSION_TRANSPORT_HEADER).equals(COOKIE_TRANSPORT, ignoreCase = true)
    }
}
