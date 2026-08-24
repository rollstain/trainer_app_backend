package app.trainer.backend.push

import java.util.UUID

data class PushPayload(
    val title: String,
    val body: String,
    val data: Map<String, String>,
)

interface PushSender {

    fun send(userIds: Collection<UUID>, payload: PushPayload)
}
