package app.trainer.backend.push

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class FcmDataTest {

    @Test
    fun `a push tells the app what it is about, next to its own data`() {
        val message = PushMessage(
            channel = PushChannel.CHAT,
            text = PushText.NEW_CHAT_MESSAGE,
            args = emptyList(),
            data = mapOf("dialogId" to "dialog"),
        )

        assertEquals(mapOf("dialogId" to "dialog", "kind" to "NEW_CHAT_MESSAGE"), fcmDataOf(message))
    }

    @Test
    fun `a summary without data still says it is the morning summary`() {
        val message = PushMessage(
            channel = PushChannel.CHAT,
            text = PushText.MORNING_SUMMARY,
            args = emptyList(),
            data = emptyMap(),
        )

        assertEquals(mapOf("kind" to "MORNING_SUMMARY"), fcmDataOf(message))
    }
}
