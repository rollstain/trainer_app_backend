package app.trainer.backend.auth.password

import app.trainer.backend.auth.SessionOpener
import app.trainer.backend.auth.SessionService
import app.trainer.backend.auth.external.ExternalIdentityEntity
import app.trainer.backend.auth.external.ExternalIdentityRepository
import app.trainer.backend.auth.external.ExternalProvider
import app.trainer.backend.auth.external.TelegramLoginService
import app.trainer.backend.auth.external.VerifiedIdentity
import app.trainer.backend.mail.MailService
import app.trainer.backend.user.UserRepository
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.server.ResponseStatusException

private const val CLAIM_TOKEN = "claim-token"
private const val TELEGRAM_SUBJECT = "telegram-777"
private const val RESET_TOKEN = "reset-token-from-the-letter"

class PasswordResetServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val credentialRepository = mock(PasswordCredentialRepository::class.java)
    private val resetTokenRepository = mock(PasswordResetTokenRepository::class.java)
    private val identityRepository = mock(ExternalIdentityRepository::class.java)
    private val telegramLoginService = mock(TelegramLoginService::class.java)
    private val mailService = mock(MailService::class.java)
    private val sessionService = mock(SessionService::class.java)
    private val sessionOpener = mock(SessionOpener::class.java)
    private val passwordEncoder = BCryptPasswordEncoder(CHEAP_BCRYPT_STRENGTH)
    private val passwordStore = PasswordStore(
        credentialRepository = credentialRepository,
        passwordEncoder = passwordEncoder,
    )

    private var sentRecipient: String? = null
    private var sentLink: String? = null

    private val service = PasswordResetService(
        userRepository = userRepository,
        passwordStore = passwordStore,
        resetTokenRepository = resetTokenRepository,
        identityRepository = identityRepository,
        telegramLoginService = telegramLoginService,
        mailService = mailService,
        sessionService = sessionService,
        sessionOpener = sessionOpener,
        properties = authProperties(),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `an unknown email gets no letter and leaves no token`() {
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(null)

        service.requestReset(ForgotPasswordRequest(email = EMAIL))

        verify(mailService, never()).sendPasswordReset(anyNonNull(), anyNonNull())
        verify(resetTokenRepository, never()).save(anyNonNull())
    }

    @Test
    fun `the letter carries a link while the database keeps only its hash`() {
        rememberSentLetter()
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(userEntity())
        `when`(resetTokenRepository.findByUserIdAndConsumedAtIsNull(USER_ID)).thenReturn(emptyList())
        `when`(mailService.resetLinkOf(anyNonNull())).thenAnswer { it.arguments[0] as String }

        service.requestReset(ForgotPasswordRequest(email = " Ivan@Mail.RU "))

        val stored = savedResetToken()
        val link = requireNotNull(sentLink)
        assertEquals(EMAIL, sentRecipient)
        assertNotEquals(link, stored.tokenHash)
        assertEquals(hashOf(link), stored.tokenHash)
        assertEquals(NOW.plusSeconds(RESET_TTL_MINUTES * SECONDS_IN_MINUTE), stored.expiresAt)
    }

    @Test
    fun `asking again within the resend window sends nothing and keeps the live link`() {
        val live = resetToken(createdAt = NOW.minusSeconds(RESEND_SECONDS / 2))
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(userEntity())
        `when`(resetTokenRepository.findByUserIdAndConsumedAtIsNull(USER_ID)).thenReturn(listOf(live))

        service.requestReset(ForgotPasswordRequest(email = EMAIL))

        verify(mailService, never()).sendPasswordReset(anyNonNull(), anyNonNull())
        assertNull(live.consumedAt)
    }

    @Test
    fun `asking again after the window retires the earlier link`() {
        val earlier = resetToken(createdAt = NOW.minusSeconds(RESEND_SECONDS * 2))
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(userEntity())
        `when`(resetTokenRepository.findByUserIdAndConsumedAtIsNull(USER_ID)).thenReturn(listOf(earlier))
        `when`(mailService.resetLinkOf(anyNonNull())).thenAnswer { it.arguments[0] as String }

        service.requestReset(ForgotPasswordRequest(email = EMAIL))

        assertEquals(NOW, earlier.consumedAt)
        verify(mailService).sendPasswordReset(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a link from the letter sets the password and signs every device out`() {
        val credential = credential()
        val token = resetToken(createdAt = NOW)
        `when`(resetTokenRepository.findByTokenHash(hashOf(RESET_TOKEN))).thenReturn(token)
        `when`(credentialRepository.findById(USER_ID)).thenReturn(Optional.of(credential))
        `when`(sessionOpener.openSession(anyNonNull(), anyNonNull())).thenReturn(openedSession())

        service.resetByEmail(resetByEmailRequest())

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, credential.passwordHash))
        assertEquals(NOW, token.consumedAt)
        verify(sessionService).revokeOtherSessions(userId = USER_ID, currentSessionId = null)
    }

    @Test
    fun `a link works only once`() {
        val token = resetToken(createdAt = NOW).apply { consumedAt = NOW.minusSeconds(SECONDS_IN_MINUTE) }
        `when`(resetTokenRepository.findByTokenHash(hashOf(RESET_TOKEN))).thenReturn(token)

        val rejected = assertFailsWith<ResponseStatusException> { service.resetByEmail(resetByEmailRequest()) }

        assertEquals(HttpStatus.CONFLICT, rejected.statusCode)
        verify(sessionOpener, never()).openSession(anyNonNull(), anyNonNull())
    }

    @Test
    fun `an expired link is refused`() {
        val token = resetToken(createdAt = NOW.minusSeconds(RESET_TTL_MINUTES * SECONDS_IN_MINUTE * 2))
        `when`(resetTokenRepository.findByTokenHash(hashOf(RESET_TOKEN))).thenReturn(token)

        val rejected = assertFailsWith<ResponseStatusException> { service.resetByEmail(resetByEmailRequest()) }

        assertEquals(HttpStatus.GONE, rejected.statusCode)
    }

    @Test
    fun `a reset gives a password to an account that never had one`() {
        givenTelegramConfirms()
        `when`(identityRepository.findByProviderAndSubjectHash(anyNonNull(), anyNonNull())).thenReturn(identity())
        `when`(credentialRepository.findById(USER_ID)).thenReturn(Optional.empty())
        `when`(sessionOpener.openSession(anyNonNull(), anyNonNull())).thenReturn(openedSession())

        service.resetByTelegram(
            PasswordResetRequest(claimToken = CLAIM_TOKEN, password = NEW_PASSWORD, deviceInfo = DEVICE)
        )

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, savedCredential().passwordHash))
    }

    @Test
    fun `a reset refuses when the telegram account is linked to nobody`() {
        givenTelegramConfirms()
        `when`(identityRepository.findByProviderAndSubjectHash(anyNonNull(), anyNonNull())).thenReturn(null)

        val rejected = assertFailsWith<ResponseStatusException> {
            service.resetByTelegram(
                PasswordResetRequest(claimToken = CLAIM_TOKEN, password = NEW_PASSWORD, deviceInfo = DEVICE)
            )
        }

        assertEquals(HttpStatus.NOT_FOUND, rejected.statusCode)
    }

    private fun resetByEmailRequest() = PasswordResetByEmailRequest(
        token = RESET_TOKEN,
        password = NEW_PASSWORD,
        deviceInfo = DEVICE,
    )

    private fun resetToken(createdAt: Instant) = PasswordResetTokenEntity(
        id = UUID.randomUUID(),
        userId = USER_ID,
        tokenHash = hashOf(RESET_TOKEN),
        createdAt = createdAt,
        expiresAt = createdAt.plusSeconds(RESET_TTL_MINUTES * SECONDS_IN_MINUTE),
        consumedAt = null,
    )

    private fun credential() = PasswordCredentialEntity(
        userId = USER_ID,
        passwordHash = passwordEncoder.encode(PASSWORD),
        failedAttempts = 0,
        lockedUntil = null,
        lockStreak = 0,
        updatedAt = NOW,
    )

    private fun identity() = ExternalIdentityEntity(
        id = UUID.randomUUID(),
        userId = USER_ID,
        provider = ExternalProvider.TELEGRAM,
        subjectHash = "hash",
        username = "ivan",
        createdAt = NOW,
    )

    private fun rememberSentLetter() {
        doAnswer { invocation ->
            sentRecipient = invocation.arguments[0] as String
            sentLink = invocation.arguments[1] as String
            null
        }.`when`(mailService).sendPasswordReset(anyNonNull(), anyNonNull())
    }

    private fun givenTelegramConfirms() {
        `when`(telegramLoginService.consumeConfirmed(CLAIM_TOKEN)).thenReturn(
            VerifiedIdentity(
                provider = ExternalProvider.TELEGRAM,
                subject = TELEGRAM_SUBJECT,
                displayName = "Иван",
                username = "ivan",
            )
        )
    }

    private fun hashOf(token: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    private fun savedResetToken(): PasswordResetTokenEntity {
        val captor = ArgumentCaptor.forClass(PasswordResetTokenEntity::class.java)
        verify(resetTokenRepository).save(captor.capture())
        return captor.value
    }

    private fun savedCredential(): PasswordCredentialEntity {
        val captor = ArgumentCaptor.forClass(PasswordCredentialEntity::class.java)
        verify(credentialRepository).save(captor.capture())
        return captor.value
    }
}
