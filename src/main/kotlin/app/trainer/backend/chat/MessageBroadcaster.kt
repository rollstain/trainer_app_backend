package app.trainer.backend.chat

import java.util.UUID

interface MessageBroadcaster {

    fun broadcast(recipientUserIds: Collection<UUID>, message: MessageResponse): Set<UUID>
}
