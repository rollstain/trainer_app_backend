package app.trainer.backend.auth.email

import app.trainer.backend.auth.password.CONFIRM_TTL_HOURS
import app.trainer.backend.auth.password.EMAIL
import app.trainer.backend.auth.password.NOW
import app.trainer.backend.auth.password.RESEND_SECONDS
import app.trainer.backend.auth.password.USER_ID
import app.trainer.backend.auth.password.anyNonNull
import app.trainer.backend.auth.password.authProperties
import app.trainer.backend.auth.password.userEntity
import app.trainer.backend.config.TooManyAttemptsException
import app.trainer.backend.mail.MailService
import app.trainer.backend.user.UserEntity
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
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private const val CONFIRM_TOKEN = "confirm-token-from-the-letter"
private const val SECONDS_IN_HOUR = 3600L
private val HOLDER_ID: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000002")

class EmailConfirmationServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val tokenRepository = mock(EmailConfirmationTokenRepository::class.java)
    private val mailService = mock(MailService::class.java)

    private var sentRecipient: String? = null
    private var sentLink: String? = null

    private val service = EmailConfirmationService(
        userRepository = userRepository,
        tokenRepository = tokenRepository,
        mailService = mailService,
        properties = authProperties(),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `the letter carries a link while the database keeps only its hash`() {
        givenMailWorks()
        val user = userEntity(emailConfirmedAt = null)
        `when`(tokenRepository.findByUserIdAndConsumedAtIsNull(USER_ID)).thenReturn(emptyList())

        service.beginQuietly(user = user, email = EMAIL)

        val stored = savedToken()
        val link = requireNotNull(sentLink)
        assertEquals(EMAIL, sentRecipient)
        assertEquals(EMAIL, stored.email)
        assertNotEquals(link, stored.tokenHash)
        assertEquals(hashOf(link), stored.tokenHash)
        assertEquals(NOW.plusSeconds(CONFIRM_TTL_HOURS * SECONDS_IN_HOUR), stored.expiresAt)
    }

    @Test
    fun `unconfigured mail leaves no token and sends nothing`() {
        `when`(mailService.isConfigured).thenReturn(false)

        service.beginQuietly(user = userEntity(emailConfirmedAt = null), email = EMAIL)

        verify(tokenRepository, never()).save(anyNonNull())
        verify(mailService, never()).sendEmailConfirmation(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a letter that failed to leave does not break the sign-up`() {
        givenMailWorks()
        `when`(tokenRepository.findByUserIdAndConsumedAtIsNull(USER_ID)).thenReturn(emptyList())
        doAnswer { throw IllegalStateException("smtp down") }
            .`when`(mailService).sendEmailConfirmation(anyNonNull(), anyNonNull())

        service.beginQuietly(user = userEntity(emailConfirmedAt = null), email = EMAIL)

        verify(tokenRepository).save(anyNonNull())
    }

    @Test
    fun `a click on the letter confirms the address`() {
        val user = userEntity(emailConfirmedAt = null)
        val token = confirmToken()
        `when`(tokenRepository.findByTokenHash(hashOf(CONFIRM_TOKEN))).thenReturn(token)
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(user))
        `when`(tokenRepository.findByUserIdAndConsumedAtIsNull(USER_ID)).thenReturn(emptyList())

        service.confirm(ConfirmEmailRequest(token = CONFIRM_TOKEN))

        assertEquals(NOW, user.emailConfirmedAt)
        assertEquals(NOW, token.consumedAt)
    }

    @Test
    fun `a link works only once`() {
        val token = confirmToken().apply { consumedAt = NOW.minusSeconds(RESEND_SECONDS) }
        `when`(tokenRepository.findByTokenHash(hashOf(CONFIRM_TOKEN))).thenReturn(token)

        val rejected = assertFailsWith<ResponseStatusException> {
            service.confirm(ConfirmEmailRequest(token = CONFIRM_TOKEN))
        }

        assertEquals(HttpStatus.CONFLICT, rejected.statusCode)
    }

    @Test
    fun `an expired link is refused`() {
        val token = confirmToken(createdAt = NOW.minusSeconds(CONFIRM_TTL_HOURS * SECONDS_IN_HOUR * 2))
        `when`(tokenRepository.findByTokenHash(hashOf(CONFIRM_TOKEN))).thenReturn(token)

        val rejected = assertFailsWith<ResponseStatusException> {
            service.confirm(ConfirmEmailRequest(token = CONFIRM_TOKEN))
        }

        assertEquals(HttpStatus.GONE, rejected.statusCode)
    }

    @Test
    fun `an unknown link is refused`() {
        `when`(tokenRepository.findByTokenHash(hashOf(CONFIRM_TOKEN))).thenReturn(null)

        val rejected = assertFailsWith<ResponseStatusException> {
            service.confirm(ConfirmEmailRequest(token = CONFIRM_TOKEN))
        }

        assertEquals(HttpStatus.NOT_FOUND, rejected.statusCode)
    }

    @Test
    fun `a click takes the address back from an unconfirmed newcomer`() {
        val user = userEntity(email = null, emailConfirmedAt = null)
        val newcomer = holderEntity(emailConfirmedAt = null)
        val token = confirmToken()
        `when`(tokenRepository.findByTokenHash(hashOf(CONFIRM_TOKEN))).thenReturn(token)
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(user))
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(newcomer)
        `when`(tokenRepository.findByUserIdAndConsumedAtIsNull(USER_ID)).thenReturn(emptyList())

        service.confirm(ConfirmEmailRequest(token = CONFIRM_TOKEN))

        assertNull(newcomer.email)
        assertEquals(EMAIL, user.email)
        assertEquals(NOW, user.emailConfirmedAt)
    }

    @Test
    fun `a click yields when the address is already confirmed elsewhere`() {
        val user = userEntity(email = null, emailConfirmedAt = null)
        val holder = holderEntity(emailConfirmedAt = NOW)
        `when`(tokenRepository.findByTokenHash(hashOf(CONFIRM_TOKEN))).thenReturn(confirmToken())
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(user))
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(holder)

        val rejected = assertFailsWith<ResponseStatusException> {
            service.confirm(ConfirmEmailRequest(token = CONFIRM_TOKEN))
        }

        assertEquals(HttpStatus.CONFLICT, rejected.statusCode)
        assertEquals(EMAIL, holder.email)
        assertNull(user.email)
    }

    @Test
    fun `a letter for a replaced address is refused`() {
        val user = userEntity(email = "new@mail.ru", emailConfirmedAt = null)
        `when`(tokenRepository.findByTokenHash(hashOf(CONFIRM_TOKEN))).thenReturn(confirmToken())
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(user))

        val rejected = assertFailsWith<ResponseStatusException> {
            service.confirm(ConfirmEmailRequest(token = CONFIRM_TOKEN))
        }

        assertEquals(HttpStatus.GONE, rejected.statusCode)
        assertNull(user.emailConfirmedAt)
    }

    @Test
    fun `asking again within the resend window tells how long to wait`() {
        val user = userEntity(emailConfirmedAt = null)
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(user))
        `when`(tokenRepository.findByUserIdAndConsumedAtIsNull(USER_ID))
            .thenReturn(listOf(confirmToken(createdAt = NOW.minusSeconds(RESEND_SECONDS / 2))))

        val rejected = assertFailsWith<TooManyAttemptsException> { service.resend(USER_ID) }

        assertEquals(RESEND_SECONDS / 2, rejected.retryAfterSeconds)
        verify(mailService, never()).sendEmailConfirmation(anyNonNull(), anyNonNull())
    }

    @Test
    fun `asking again after the window retires the earlier link`() {
        givenMailWorks()
        val user = userEntity(emailConfirmedAt = null)
        val earlier = confirmToken(createdAt = NOW.minusSeconds(RESEND_SECONDS * 2))
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(user))
        `when`(tokenRepository.findByUserIdAndConsumedAtIsNull(USER_ID)).thenReturn(listOf(earlier))

        service.resend(USER_ID)

        assertEquals(NOW, earlier.consumedAt)
        verify(mailService).sendEmailConfirmation(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a confirmed address needs no more letters`() {
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(userEntity(emailConfirmedAt = NOW)))

        val rejected = assertFailsWith<ResponseStatusException> { service.resend(USER_ID) }

        assertEquals(HttpStatus.CONFLICT, rejected.statusCode)
    }

    @Test
    fun `an unconfirmed holder frees the address for the newcomer`() {
        val holder = holderEntity(emailConfirmedAt = null)
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(holder)

        val freed = service.freeUnconfirmedHolder(email = EMAIL, ownerId = null)

        assertEquals(true, freed)
        assertNull(holder.email)
    }

    @Test
    fun `a confirmed holder keeps the address`() {
        val holder = holderEntity(emailConfirmedAt = NOW)
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(holder)

        val freed = service.freeUnconfirmedHolder(email = EMAIL, ownerId = null)

        assertEquals(false, freed)
        assertEquals(EMAIL, holder.email)
    }

    private fun givenMailWorks() {
        `when`(mailService.isConfigured).thenReturn(true)
        `when`(mailService.confirmLinkOf(anyNonNull())).thenAnswer { it.arguments[0] as String }
        doAnswer { invocation ->
            sentRecipient = invocation.arguments[0] as String
            sentLink = invocation.arguments[1] as String
            null
        }.`when`(mailService).sendEmailConfirmation(anyNonNull(), anyNonNull())
    }

    private fun confirmToken(createdAt: Instant = NOW, email: String = EMAIL) = EmailConfirmationTokenEntity(
        id = UUID.randomUUID(),
        userId = USER_ID,
        email = email,
        tokenHash = hashOf(CONFIRM_TOKEN),
        createdAt = createdAt,
        expiresAt = createdAt.plusSeconds(CONFIRM_TTL_HOURS * SECONDS_IN_HOUR),
        consumedAt = null,
    )

    private fun holderEntity(emailConfirmedAt: Instant?) = UserEntity(
        id = HOLDER_ID,
        displayName = "Пётр",
        phone = null,
        email = EMAIL,
        emailConfirmedAt = emailConfirmedAt,
        login = null,
        isOwner = false,
        createdAt = NOW,
    )

    private fun hashOf(token: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    private fun savedToken(): EmailConfirmationTokenEntity {
        val captor = ArgumentCaptor.forClass(EmailConfirmationTokenEntity::class.java)
        verify(tokenRepository).save(captor.capture())
        return captor.value
    }
}
