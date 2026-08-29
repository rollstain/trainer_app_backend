package app.trainer.backend.auth.external

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

private const val START_CODE_BYTES = 12
private const val CLAIM_TOKEN_BYTES = 32
private const val LOGIN_TTL_MINUTES = 10L
private const val CLAIM_TTL_MINUTES = 1440L
private const val FORGOTTEN_LOGIN_HOURS = 1L

@Service
class TelegramLoginService(
    private val loginRepository: TelegramLoginRepository,
    private val properties: TelegramProperties,
    private val clock: Clock,
) {

    private val random = SecureRandom()
    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    @Transactional
    fun startClaim(targetUserId: UUID): TelegramStartResponse = start(targetUserId = targetUserId)

    @Transactional
    fun start(): TelegramStartResponse = start(targetUserId = null)

    private fun start(targetUserId: UUID?): TelegramStartResponse {
        val botName = properties.botUsername.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "Вход через Telegram не настроен")
        val now = Instant.now(clock)
        loginRepository.deleteByCreatedAtBefore(now.minus(FORGOTTEN_LOGIN_HOURS, ChronoUnit.HOURS))

        val startCode = randomText(START_CODE_BYTES)
        val claimToken = randomText(CLAIM_TOKEN_BYTES)
        loginRepository.save(
            TelegramLoginEntity(
                id = UUID.randomUUID(),
                startCode = startCode,
                claimTokenHash = hashOf(claimToken),
                telegramUserId = null,
                telegramDisplayName = null,
                telegramUsername = null,
                targetUserId = targetUserId,
                createdAt = now,
                confirmedAt = null,
                consumedAt = null,
            )
        )
        return TelegramStartResponse(
            claimToken = claimToken,
            deepLink = "https://t.me/$botName?start=$startCode",
        )
    }

    @Transactional
    fun confirm(
        startCode: String,
        telegramUserId: String,
        telegramDisplayName: String?,
        telegramUsername: String?,
    ): ConfirmedTelegramLogin? {
        val login = loginRepository.findByStartCode(startCode) ?: return null
        if (login.consumedAt != null || isExpired(login)) return null
        login.telegramUserId = telegramUserId
        login.telegramDisplayName = telegramDisplayName
        login.telegramUsername = telegramUsername
        login.confirmedAt = Instant.now(clock)
        val targetUserId = login.targetUserId ?: return ConfirmedTelegramLogin(targetUserId = null, identity = null)
        login.consumedAt = login.confirmedAt
        return ConfirmedTelegramLogin(
            targetUserId = targetUserId,
            identity = VerifiedIdentity(
                provider = ExternalProvider.TELEGRAM,
                subject = telegramUserId,
                displayName = telegramDisplayName?.takeIf { it.isNotBlank() },
                username = telegramUsername?.takeIf { it.isNotBlank() },
            ),
        )
    }

    @Transactional
    fun consumeConfirmed(claimToken: String): VerifiedIdentity {
        val login = loginRepository.findByClaimTokenHash(hashOf(claimToken))
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Telegram не подтвердил вход")
        if (login.consumedAt != null || isExpired(login)) {
            throw ResponseStatusException(HttpStatus.GONE, "Срок входа через Telegram истёк")
        }
        val telegramUserId = login.telegramUserId
        if (login.confirmedAt == null || telegramUserId == null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Вход через Telegram ещё не подтверждён")
        }
        login.consumedAt = Instant.now(clock)
        return VerifiedIdentity(
            provider = ExternalProvider.TELEGRAM,
            subject = telegramUserId,
            displayName = login.telegramDisplayName?.takeIf { it.isNotBlank() },
            username = login.telegramUsername?.takeIf { it.isNotBlank() },
        )
    }

    private fun isExpired(login: TelegramLoginEntity): Boolean {
        val minutes = if (login.targetUserId == null) LOGIN_TTL_MINUTES else CLAIM_TTL_MINUTES
        return login.createdAt.plus(minutes, ChronoUnit.MINUTES).isBefore(Instant.now(clock))
    }

    private fun randomText(bytes: Int): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return encoder.encodeToString(buffer)
    }

    private fun hashOf(value: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
}
