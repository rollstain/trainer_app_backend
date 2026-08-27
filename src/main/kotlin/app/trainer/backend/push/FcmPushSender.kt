package app.trainer.backend.push

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import java.util.UUID
import org.slf4j.LoggerFactory

private const val FCM_BATCH_LIMIT = 500

class FcmPushSender(
    private val messaging: FirebaseMessaging,
    private val tokenRepository: PushTokenRepository,
    private val pushTexts: PushTexts,
) : PushSender {

    private val logger = LoggerFactory.getLogger(FcmPushSender::class.java)

    override fun send(userIds: Collection<UUID>, message: PushMessage) {
        if (userIds.isEmpty()) return
        tokenRepository
            .findByUserIdIn(userIds)
            .groupBy { localeOfToken(it.locale) }
            .forEach { (locale, tokensOfLocale) ->
                val rendered = pushTexts.render(text = message.text, locale = locale)
                tokensOfLocale
                    .map { it.token }
                    .chunked(FCM_BATCH_LIMIT)
                    .forEach { batch -> sendBatch(tokens = batch, message = message, rendered = rendered) }
            }
    }

    private fun sendBatch(tokens: List<String>, message: PushMessage, rendered: RenderedPush) {
        val multicast = MulticastMessage.builder()
            .setNotification(
                Notification.builder()
                    .setTitle(rendered.title)
                    .setBody(rendered.body)
                    .build()
            )
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setNotification(
                        AndroidNotification.builder()
                            .setChannelId(message.channel.androidChannelId)
                            .build()
                    )
                    .build()
            )
            .putAllData(message.data)
            .addAllTokens(tokens)
            .build()

        val response = runCatching { messaging.sendEachForMulticast(multicast) }.getOrElse { failure ->
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

    override fun send(userIds: Collection<UUID>, message: PushMessage) {
        logger.info(
            "Пуши не настроены (нет trainer.push.credentials-path), пропущено: {} получателей, текст {}",
            userIds.size,
            message.text,
        )
    }
}
