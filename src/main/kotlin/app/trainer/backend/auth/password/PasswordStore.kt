package app.trainer.backend.auth.password

import java.time.Instant
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class PasswordStore(
    private val credentialRepository: PasswordCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    fun credentialOf(userId: UUID): PasswordCredentialEntity? = credentialRepository.findByIdOrNull(userId)

    fun matches(credential: PasswordCredentialEntity, password: String): Boolean =
        passwordEncoder.matches(password, credential.passwordHash)

    fun save(userId: UUID, password: String, now: Instant) {
        val credential = credentialRepository.findByIdOrNull(userId)
        if (credential == null) {
            credentialRepository.save(
                PasswordCredentialEntity(
                    userId = userId,
                    passwordHash = passwordEncoder.encode(password),
                    failedAttempts = 0,
                    lockedUntil = null,
                    lockStreak = 0,
                    updatedAt = now,
                )
            )
            return
        }
        credential.passwordHash = passwordEncoder.encode(password)
        credential.failedAttempts = 0
        credential.lockedUntil = null
        credential.lockStreak = 0
        credential.updatedAt = now
    }
}
