package app.trainer.backend.auth

import java.security.SecureRandom
import org.springframework.stereotype.Component

private const val INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val INVITE_CODE_LENGTH = 6

@Component
class InviteCodeGenerator(private val inviteRepository: InviteRepository) {

    private val random = SecureRandom()

    fun nextUnusedCode(): String {
        var code = randomCode()
        while (inviteRepository.findByCode(code) != null) {
            code = randomCode()
        }
        return code
    }

    private fun randomCode(): String {
        val builder = StringBuilder(INVITE_CODE_LENGTH)
        repeat(INVITE_CODE_LENGTH) {
            builder.append(INVITE_CODE_ALPHABET[random.nextInt(INVITE_CODE_ALPHABET.length)])
        }
        return builder.toString()
    }
}
