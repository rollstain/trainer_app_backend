package app.trainer.backend.auth.external

import java.security.MessageDigest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

const val TELEGRAM_BOT_SECRET_HEADER = "X-Telegram-Bot-Secret"
private const val BOT_SECRET_MIN_LENGTH = 16

@Component
class TelegramBotGuard(private val telegramProperties: TelegramProperties) {

    fun authorize(secret: String?) {
        val configured = telegramProperties.botSecret
        if (configured.length < BOT_SECRET_MIN_LENGTH) {
            throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Бот не настроен")
        }
        val matches = MessageDigest.isEqual(
            secret.orEmpty().toByteArray(Charsets.UTF_8),
            configured.toByteArray(Charsets.UTF_8),
        )
        if (!matches) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Бот не опознан")
        }
    }
}
