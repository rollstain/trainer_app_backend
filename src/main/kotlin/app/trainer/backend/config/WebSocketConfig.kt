package app.trainer.backend.config

import app.trainer.backend.chat.ChatWebSocketHandler
import app.trainer.backend.chat.WEB_SOCKET_USER_ID_ATTRIBUTE
import app.trainer.backend.chat.WebSocketTicketService
import java.util.UUID
import org.slf4j.LoggerFactory
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
import org.springframework.web.util.UriComponentsBuilder

private const val BEARER_PREFIX = "Bearer "
private const val TICKET_QUERY_PARAMETER = "ticket"

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val chatWebSocketHandler: ChatWebSocketHandler,
    private val jwtDecoder: JwtDecoder,
    private val webSocketTicketService: WebSocketTicketService,
    private val webClientProperties: WebClientProperties,
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry
            .addHandler(chatWebSocketHandler, "/ws/chat")
            .addInterceptors(TokenHandshakeInterceptor(jwtDecoder, webSocketTicketService))
            .setAllowedOriginPatterns(*webClientProperties.allowedOrigins.toTypedArray())
    }
}

private class TokenHandshakeInterceptor(
    private val jwtDecoder: JwtDecoder,
    private val webSocketTicketService: WebSocketTicketService,
) : HandshakeInterceptor {

    private val logger = LoggerFactory.getLogger(TokenHandshakeInterceptor::class.java)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val userId = userIdFromAccessToken(request) ?: userIdFromTicket(request) ?: return false
        attributes[WEB_SOCKET_USER_ID_ATTRIBUTE] = userId
        return true
    }

    private fun userIdFromAccessToken(request: ServerHttpRequest): UUID? {
        val token = request.headers.getFirst(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?: return null
        return try {
            UUID.fromString(jwtDecoder.decode(token).subject)
        } catch (invalidToken: JwtException) {
            logger.debug("Рукопожатие отклонено: токен не разобран", invalidToken)
            null
        } catch (invalidSubject: IllegalArgumentException) {
            logger.debug("Рукопожатие отклонено: в токене не userId", invalidSubject)
            null
        }
    }

    private fun userIdFromTicket(request: ServerHttpRequest): UUID? {
        val ticket = UriComponentsBuilder.fromUri(request.uri).build()
            .queryParams
            .getFirst(TICKET_QUERY_PARAMETER)
            ?: return null
        return webSocketTicketService.consume(ticket)
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) = Unit
}
