package app.trainer.backend.auth.email

import app.trainer.backend.auth.AuthProperties
import app.trainer.backend.config.TooManyAttemptsException
import app.trainer.backend.mail.MailService
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val CONFIRM_TOKEN_BYTES = 32

@Service
class EmailConfirmationService(
    private val userRepository: UserRepository,
    private val tokenRepository: EmailConfirmationTokenRepository,
    private val mailService: MailService,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    private val logger = LoggerFactory.getLogger(EmailConfirmationService::class.java)
    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun beginQuietly(user: UserEntity, email: String) {
        if (!mailService.isConfigured) return
        val link = issueLink(user = user, email = email)
        runCatching { mailService.sendEmailConfirmation(recipient = email, link = link) }
            .onFailure { logger.warn("Письмо подтверждения почты пользователю {} не ушло", user.id, it) }
    }

    @Transactional
    fun resend(userId: UUID) {
        val user = userRepository.findByIdOrNull(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден")
        val email = user.email
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "У аккаунта нет почты")
        if (user.emailConfirmedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Почта уже подтверждена")
        }
        val now = Instant.now(clock)
        tokenRepository.findByUserIdAndConsumedAtIsNull(user.id)
            .firstOrNull { justSent(token = it, now = now) }
            ?.let { throw tooEarlyToResend(token = it, now = now) }

        mailService.sendEmailConfirmation(recipient = email, link = issueLink(user = user, email = email))
    }

    @Transactional
    fun confirm(request: ConfirmEmailRequest) {
        val now = Instant.now(clock)
        val token = tokenRepository.findByTokenHash(hashOf(request.token))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ссылка не найдена")
        if (token.consumedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Ссылка уже использована")
        }
        if (token.expiresAt.isBefore(now)) {
            throw ResponseStatusException(HttpStatus.GONE, "Срок ссылки истёк")
        }
        token.consumedAt = now

        val user = userRepository.findByIdOrNull(token.userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Пользователь не найден")
        if (user.email != null && user.email != token.email) {
            throw ResponseStatusException(HttpStatus.GONE, "У аккаунта уже другая почта")
        }
        if (user.email == null) {
            if (!freeUnconfirmedHolder(email = token.email, ownerId = user.id)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Эта почта уже подтверждена в другом аккаунте")
            }
            user.email = token.email
        }
        user.emailConfirmedAt = now
        tokenRepository.findByUserIdAndConsumedAtIsNull(user.id).forEach { it.consumedAt = now }
    }

    fun freeUnconfirmedHolder(email: String, ownerId: UUID?): Boolean {
        val holder = userRepository.findByEmail(email) ?: return true
        if (holder.id == ownerId) return true
        if (holder.emailConfirmedAt != null) return false
        holder.email = null
        userRepository.flush()
        return true
    }

    private fun issueLink(user: UserEntity, email: String): String {
        val now = Instant.now(clock)
        tokenRepository.findByUserIdAndConsumedAtIsNull(user.id).forEach { it.consumedAt = now }
        val token = randomToken()
        tokenRepository.save(
            EmailConfirmationTokenEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                email = email,
                tokenHash = hashOf(token),
                createdAt = now,
                expiresAt = now.plus(properties.emailConfirmTtlHours, ChronoUnit.HOURS),
                consumedAt = null,
            )
        )
        return mailService.confirmLinkOf(token)
    }

    private fun tooEarlyToResend(token: EmailConfirmationTokenEntity, now: Instant): TooManyAttemptsException {
        val readyAt = token.createdAt.plusSeconds(properties.emailConfirmResendSeconds)
        return TooManyAttemptsException(
            retryAfterSeconds = ChronoUnit.SECONDS.between(now, readyAt),
            explanation = "Письмо уже отправлено — проверьте почту или подождите",
        )
    }

    private fun justSent(token: EmailConfirmationTokenEntity, now: Instant): Boolean =
        token.createdAt.plusSeconds(properties.emailConfirmResendSeconds).isAfter(now)

    private fun randomToken(): String {
        val buffer = ByteArray(CONFIRM_TOKEN_BYTES)
        random.nextBytes(buffer)
        return encoder.encodeToString(buffer)
    }

    private fun hashOf(token: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
