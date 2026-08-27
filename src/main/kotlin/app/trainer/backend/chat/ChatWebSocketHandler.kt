package app.trainer.backend.chat

import com.fasterxml.jackson.databind.ObjectMapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator
import org.springframework.web.socket.handler.TextWebSocketHandler

const val WEB_SOCKET_USER_ID_ATTRIBUTE = "userId"

private const val SEND_TIME_LIMIT_MS = 5_000
private const val SEND_BUFFER_SIZE_BYTES = 512 * 1024

@Component
class ChatWebSocketHandler(private val objectMapper: ObjectMapper) : TextWebSocketHandler(), MessageBroadcaster {

    private val sessionsByUser = ConcurrentHashMap<UUID, ConcurrentHashMap<String, WebSocketSession>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = userIdOf(session) ?: return
        sessionsByUser.computeIfAbsent(userId) { ConcurrentHashMap() }[session.id] =
            ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_BYTES)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val userId = userIdOf(session) ?: return
        val sessions = sessionsByUser[userId] ?: return
        sessions.remove(session.id)
        if (sessions.isEmpty()) sessionsByUser.remove(userId)
    }

    override fun broadcast(recipientUserIds: Collection<UUID>, message: MessageResponse): Set<UUID> {
        val payload = TextMessage(objectMapper.writeValueAsString(message))
        val delivered = mutableSetOf<UUID>()
        recipientUserIds.forEach { userId ->
            val openSessions = sessionsByUser[userId]?.values?.filter { it.isOpen }.orEmpty()
            openSessions.forEach { session -> session.sendMessage(payload) }
            if (openSessions.isNotEmpty()) delivered.add(userId)
        }
        return delivered
    }

    private fun userIdOf(session: WebSocketSession): UUID? {
        return session.attributes[WEB_SOCKET_USER_ID_ATTRIBUTE] as? UUID
    }
}
