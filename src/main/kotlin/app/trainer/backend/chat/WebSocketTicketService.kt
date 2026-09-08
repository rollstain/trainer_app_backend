package app.trainer.backend.chat

import app.trainer.backend.auth.TokenService
import app.trainer.backend.config.CurrentUserId
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

private const val TICKET_TTL_SECONDS = 30L

data class WebSocketTicketResponse(
    val ticket: String,
    val expiresAt: Instant,
)

@Service
class WebSocketTicketService(
    private val tokenService: TokenService,
    private val clock: Clock,
) {

    private val issuedTickets = ConcurrentHashMap<String, IssuedTicket>()

    fun issue(userId: UUID): WebSocketTicketResponse {
        val now = Instant.now(clock)
        dropExpired(now)
        val ticket = tokenService.generateOpaqueToken()
        val expiresAt = now.plusSeconds(TICKET_TTL_SECONDS)
        issuedTickets[tokenService.hash(ticket)] = IssuedTicket(userId = userId, expiresAt = expiresAt)
        return WebSocketTicketResponse(ticket = ticket, expiresAt = expiresAt)
    }

    fun consume(ticket: String): UUID? {
        val claimed = issuedTickets.remove(tokenService.hash(ticket)) ?: return null
        return claimed.userId.takeIf { claimed.expiresAt.isAfter(Instant.now(clock)) }
    }

    private fun dropExpired(now: Instant) {
        issuedTickets.values.removeIf { it.expiresAt.isBefore(now) }
    }

    private data class IssuedTicket(val userId: UUID, val expiresAt: Instant)
}

@RestController
class WebSocketTicketController(private val webSocketTicketService: WebSocketTicketService) {

    @PostMapping("/ws/ticket")
    fun issue(@CurrentUserId userId: UUID): WebSocketTicketResponse {
        return webSocketTicketService.issue(userId = userId)
    }
}
