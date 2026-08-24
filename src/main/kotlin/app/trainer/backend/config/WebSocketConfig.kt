package app.trainer.backend.config

import app.trainer.backend.chat.ChatWebSocketHandler
import app.trainer.backend.chat.WEB_SOCKET_USER_ID_ATTRIBUTE
import java.util.UUID
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.HandshakeInterceptor

private const val BEARER_PREFIX = "Bearer "

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val chatWebSocketHandler: ChatWebSocketHandler,
    private val jwtDecoder: JwtDecoder,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(chatWebSocketHandler, "/ws/chat")
            .addInterceptors(TokenHandshakeInterceptor(jwtDecoder))
            .setAllowedOriginPatterns("*")
    }
}

private class TokenHandshakeInterceptor(private val jwtDecoder: JwtDecoder) : HandshakeInterceptor {

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val token = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?: return false
        return try {
            attributes[WEB_SOCKET_USER_ID_ATTRIBUTE] = UUID.fromString(jwtDecoder.decode(token).subject)
            true
        } catch (invalidToken: JwtException) {
            false
        } catch (invalidSubject: IllegalArgumentException) {
            false
        }
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit
}
