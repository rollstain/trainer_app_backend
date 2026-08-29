package app.trainer.backend.auth.password

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val EMAIL_FORMAT = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$")
private val LOGIN_FORMAT = Regex("^[a-z0-9][a-z0-9._-]{1,30}[a-z0-9]$")

const val PASSWORD_MIN_LENGTH = 8
const val BCRYPT_MAX_PASSWORD_BYTES = 72

fun normalizedEmailOrNull(raw: String): String? = raw.trim().lowercase().takeIf(EMAIL_FORMAT::matches)

fun normalizedLoginOrNull(raw: String): String? = raw.trim().lowercase().takeIf(LOGIN_FORMAT::matches)

fun normalizedIdentifier(raw: String): String = raw.trim().lowercase()

fun requireAcceptablePassword(password: String) {
    val acceptable = password.length >= PASSWORD_MIN_LENGTH &&
        password.toByteArray(Charsets.UTF_8).size <= BCRYPT_MAX_PASSWORD_BYTES
    if (!acceptable) {
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль короче $PASSWORD_MIN_LENGTH символов")
    }
}
