package app.trainer.backend.chat

import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import java.util.UUID
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

data class MessageSentEvent(
    val message: MessageResponse,
    val recipientUserIds: Set<UUID>,
    val push: PushMessage,
)

@Component
class MessageDeliveryListener(
    private val broadcaster: MessageBroadcaster,
    private val pushSender: PushSender,
) {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMessageSent(event: MessageSentEvent) {
        val delivered = broadcaster.broadcast(
            recipientUserIds = event.recipientUserIds,
            message = event.message,
        )
        val offlineUserIds = event.recipientUserIds - delivered
        if (offlineUserIds.isEmpty()) return
        pushSender.send(userIds = offlineUserIds, message = event.push)
    }
}
