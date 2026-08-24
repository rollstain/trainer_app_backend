package app.trainer.backend.push

import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional

private const val FCM_BATCH_LIMIT = 500

class FcmPushSender(
    private val messaging: FirebaseMessaging,
    private val tokenRepository: PushTokenRepository,
) : PushSender {

    private val logger = LoggerFactory.getLogger(FcmPushSender::class.java)

    @Transactional
    override fun send(userIds: Collection<UUID>, payload: PushPayload) {
        if (userIds.isEmpty()) return
        val tokens = tokenRepository.findByUserIdIn(userIds).map { it.token }.distinct()
        if (tokens.isEmpty()) return

        tokens.chunked(FCM_BATCH_LIMIT).forEach { batch -> sendBatch(tokens = batch, payload = payload) }
    }

    private fun sendBatch(tokens: List<String>, payload: PushPayload) {
        val message = MulticastMessage.builder()
            .setNotification(
                Notification.builder()
                    .setTitle(payload.title)
                    .setBody(payload.body)
                    .build()
            )
            .putAllData(payload.data)
            .addAllTokens(tokens)
            .build()

        val response = runCatching { messaging.sendEachForMulticast(message) }.getOrElse { failure ->
            logger.error("Не удалось отправить пуш на {} токенов", tokens.size, failure)
            return
        }
        response.responses.forEachIndexed { index, single ->
            if (single.isSuccessful) return@forEachIndexed
            val token = tokens[index]
            if (isTokenDead(single.exception)) {
                logger.info("Токен больше не действителен, удаляем")
                tokenRepository.deleteByToken(token)
            } else {
                logger.warn("Пуш не доставлен: {}", single.exception?.message)
            }
        }
    }

    private fun isTokenDead(exception: FirebaseMessagingException?): Boolean {
        val code = exception?.messagingErrorCode ?: return false
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT
    }
}

class NoOpPushSender : PushSender {

    private val logger = LoggerFactory.getLogger(NoOpPushSender::class.java)

    override fun send(userIds: Collection<UUID>, payload: PushPayload) {
        logger.info(
            "Пуши не настроены (нет trainer.push.credentials-path), пропущено получателей: {}",
            userIds.size,
        )
    }
}
