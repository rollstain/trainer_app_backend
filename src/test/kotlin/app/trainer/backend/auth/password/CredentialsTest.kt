package app.trainer.backend.auth.password

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private const val CYRILLIC_LETTER_BYTES = 2

class CredentialsTest {

    @Test
    fun `короткий пароль отвергается по длине`() {
        val failure = assertFailsWith<ResponseStatusException> { requireAcceptablePassword("1234567") }

        assertEquals(HttpStatus.BAD_REQUEST, failure.statusCode)
        assertEquals("Пароль короче $PASSWORD_MIN_LENGTH символов", failure.reason)
    }

    @Test
    fun `длинная русская фраза отвергается по размеру, а не как короткая`() {
        val tooLong = "п".repeat(BCRYPT_MAX_PASSWORD_BYTES / CYRILLIC_LETTER_BYTES + 1)

        val failure = assertFailsWith<ResponseStatusException> { requireAcceptablePassword(tooLong) }

        assertEquals(HttpStatus.BAD_REQUEST, failure.statusCode)
        assertEquals(
            "Пароль не помещается в $BCRYPT_MAX_PASSWORD_BYTES байт: русская буква занимает два",
            failure.reason,
        )
    }

    @Test
    fun `русская фраза ровно по границе принимается`() {
        val atLimit = "п".repeat(BCRYPT_MAX_PASSWORD_BYTES / CYRILLIC_LETTER_BYTES)

        requireAcceptablePassword(atLimit)
    }
}
