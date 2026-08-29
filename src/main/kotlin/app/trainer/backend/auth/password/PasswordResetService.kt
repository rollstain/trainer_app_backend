package app.trainer.backend.auth.password

import app.trainer.backend.auth.AuthProperties
import app.trainer.backend.auth.AuthTokensResponse
import app.trainer.backend.auth.SessionOpener
import app.trainer.backend.auth.SessionService
import app.trainer.backend.auth.external.ExternalIdentityRepository
import app.trainer.backend.auth.external.ExternalProvider
import app.trainer.backend.auth.external.TelegramLoginService
import app.trainer.backend.auth.external.subjectHashOf
import app.trainer.backend.mail.MailService
import app.trainer.backend.user.UserRepository
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val RESET_TOKEN_BYTES = 32

@Service
class PasswordResetService(
    private val userRepository: UserRepository,
    private val passwordStore: PasswordStore,
    private val resetTokenRepository: PasswordResetTokenRepository,
    private val identityRepository: ExternalIdentityRepository,
    private val telegramLoginService: TelegramLoginService,
    private val mailService: MailService,
    private val sessionService: SessionService,
    private val sessionOpener: SessionOpener,
    private val properties: AuthProperties,
    private val clock: Clock,
) {

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    @Transactional
    fun requestReset(request: ForgotPasswordRequest) {
        val email = normalizedEmailOrNull(request.email) ?: return
        val user = userRepository.findByEmail(email) ?: return
        val now = Instant.now(clock)
        val live = resetTokenRepository.findByUserIdAndConsumedAtIsNull(user.id)
        if (live.any { justSent(token = it, now = now) }) return
        live.forEach { it.consumedAt = now }

        val token = randomToken()
        resetTokenRepository.save(
            PasswordResetTokenEntity(
                id = UUID.randomUUID(),
                userId = user.id,
                tokenHash = hashOf(token),
                createdAt = now,
                expiresAt = now.plus(properties.passwordResetTtlMinutes, ChronoUnit.MINUTES),
                consumedAt = null,
            )
        )
        mailService.sendPasswordReset(recipient = email, link = mailService.resetLinkOf(token))
    }

    @Transactional
    fun resetByEmail(request: PasswordResetByEmailRequest): AuthTokensResponse {
        requireAcceptablePassword(request.password)
        val now = Instant.now(clock)
        val token = resetTokenRepository.findByTokenHash(hashOf(request.token))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ссылка не найдена")
        if (token.consumedAt != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Ссылка уже использована")
        }
        if (token.expiresAt.isBefore(now)) {
            throw ResponseStatusException(HttpStatus.GONE, "Срок ссылки истёк")
        }
        token.consumedAt = now

        return startOverWithPassword(
            userId = token.userId,
            password = request.password,
            deviceInfo = request.deviceInfo,
        )
    }

    @Transactional
    fun resetByTelegram(request: PasswordResetRequest): AuthTokensResponse {
        requireAcceptablePassword(request.password)
        val verified = telegramLoginService.consumeConfirmed(request.claimToken)
        val identity = identityRepository.findByProviderAndSubjectHash(
            provider = ExternalProvider.TELEGRAM,
            subjectHash = subjectHashOf(verified),
        ) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Этот Telegram не привязан ни к одному аккаунту")

        return startOverWithPassword(
            userId = identity.userId,
            password = request.password,
            deviceInfo = request.deviceInfo,
        )
    }

    private fun startOverWithPassword(userId: UUID, password: String, deviceInfo: String): AuthTokensResponse {
        passwordStore.save(userId = userId, password = password, now = Instant.now(clock))
        sessionService.revokeOtherSessions(userId = userId, currentSessionId = null)
        return sessionOpener.openSession(userId = userId, deviceInfo = deviceInfo)
    }

    private fun justSent(token: PasswordResetTokenEntity, now: Instant): Boolean =
        token.createdAt.plusSeconds(properties.passwordResetResendSeconds).isAfter(now)

    private fun randomToken(): String {
        val buffer = ByteArray(RESET_TOKEN_BYTES)
        random.nextBytes(buffer)
        return encoder.encodeToString(buffer)
    }

    private fun hashOf(token: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))
}
