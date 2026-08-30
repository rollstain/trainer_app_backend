package app.trainer.backend.auth.password

import app.trainer.backend.auth.AuthProperties
import app.trainer.backend.auth.AuthTokensResponse
import app.trainer.backend.auth.SessionOpener
import app.trainer.backend.auth.SessionService
import app.trainer.backend.auth.email.EmailConfirmationService
import app.trainer.backend.config.FieldConflictException
import app.trainer.backend.config.TooManyAttemptsException
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.web.server.ResponseStatusException

internal val NOW: Instant = Instant.parse("2026-08-29T10:00:00Z")
internal val USER_ID: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000001")
internal const val DEVICE = "Pixel 8"
internal const val EMAIL = "ivan@mail.ru"
internal const val LOGIN = "ivan.trainer"
internal const val PASSWORD = "very-secret-1"
internal const val NEW_PASSWORD = "another-secret-2"
internal const val MAX_FAILED_ATTEMPTS = 5
internal const val LOCK_MINUTES = 5L
internal const val LOCK_MAX_MINUTES = 30L
internal const val RESET_TTL_MINUTES = 60L
internal const val RESEND_SECONDS = 120L
internal const val CONFIRM_TTL_HOURS = 72L
internal const val SECONDS_IN_MINUTE = 60L
internal const val CHEAP_BCRYPT_STRENGTH = 4

private val SESSION_ID: UUID = UUID.fromString("c0000000-0000-0000-0000-000000000009")

@Suppress("UNCHECKED_CAST")
internal fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

internal fun authProperties() = AuthProperties(
    accessTokenTtlMinutes = 15,
    refreshTokenIdleDays = 90,
    refreshTokenAbsoluteDays = 365,
    refreshRotationGraceSeconds = 60,
    inviteTtlHours = 168,
    passwordMaxFailedAttempts = MAX_FAILED_ATTEMPTS,
    passwordLockMinutes = LOCK_MINUTES,
    passwordLockMaxMinutes = LOCK_MAX_MINUTES,
    passwordResetTtlMinutes = RESET_TTL_MINUTES,
    passwordResetResendSeconds = RESEND_SECONDS,
    emailConfirmTtlHours = CONFIRM_TTL_HOURS,
    emailConfirmResendSeconds = RESEND_SECONDS,
    jwtSecret = "0123456789012345678901234567890123456789",
    adminToken = "admin-token",
)

internal fun userEntity(email: String? = EMAIL, emailConfirmedAt: Instant? = NOW) = UserEntity(
    id = USER_ID,
    displayName = "Иван",
    phone = null,
    email = email,
    emailConfirmedAt = emailConfirmedAt,
    login = LOGIN,
    isOwner = false,
    createdAt = NOW,
)

internal fun openedSession() = AuthTokensResponse(
    accessToken = "access",
    refreshToken = "refresh",
    accessTokenExpiresAt = NOW,
)

class PasswordAuthServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val credentialRepository = mock(PasswordCredentialRepository::class.java)
    private val sessionService = mock(SessionService::class.java)
    private val sessionOpener = mock(SessionOpener::class.java)
    private val passwordEncoder = BCryptPasswordEncoder(CHEAP_BCRYPT_STRENGTH)
    private val passwordStore = PasswordStore(
        credentialRepository = credentialRepository,
        passwordEncoder = passwordEncoder,
    )

    private val emailConfirmationService = mock(EmailConfirmationService::class.java)

    private val service = PasswordAuthService(
        userRepository = userRepository,
        passwordStore = passwordStore,
        sessionService = sessionService,
        sessionOpener = sessionOpener,
        emailConfirmationService = emailConfirmationService,
        properties = authProperties(),
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `sign-up stores a lowercase email and opens a session`() {
        `when`(userRepository.save(anyNonNull<UserEntity>())).thenAnswer { it.arguments[0] as UserEntity }
        `when`(emailConfirmationService.freeUnconfirmedHolder(email = EMAIL, ownerId = null)).thenReturn(true)
        givenSessionOpens()

        service.signUp(
            PasswordSignUpRequest(
                displayName = " Иван ",
                email = " Ivan@Mail.RU ",
                login = "Ivan.Trainer",
                password = PASSWORD,
                deviceInfo = DEVICE,
            )
        )

        val saved = savedUser()
        assertEquals(EMAIL, saved.email)
        assertEquals(LOGIN, saved.login)
        assertEquals("Иван", saved.displayName)
        assertTrue(passwordEncoder.matches(PASSWORD, savedCredential().passwordHash))
    }

    @Test
    fun `sign-up leaves the address unconfirmed and asks to confirm it`() {
        `when`(userRepository.save(anyNonNull<UserEntity>())).thenAnswer { it.arguments[0] as UserEntity }
        `when`(emailConfirmationService.freeUnconfirmedHolder(email = EMAIL, ownerId = null)).thenReturn(true)
        givenSessionOpens()

        service.signUp(
            PasswordSignUpRequest(
                displayName = "Иван",
                email = EMAIL,
                login = null,
                password = PASSWORD,
                deviceInfo = DEVICE,
            )
        )

        val saved = savedUser()
        assertNull(saved.emailConfirmedAt)
        verify(emailConfirmationService).beginQuietly(user = saved, email = EMAIL)
    }

    @Test
    fun `sign-up rejects an email already taken in another letter case`() {
        `when`(emailConfirmationService.freeUnconfirmedHolder(email = EMAIL, ownerId = null)).thenReturn(false)

        val rejected = assertFailsWith<FieldConflictException> {
            service.signUp(
                PasswordSignUpRequest(
                    displayName = "Иван",
                    email = "IVAN@MAIL.RU",
                    login = null,
                    password = PASSWORD,
                    deviceInfo = DEVICE,
                )
            )
        }

        assertEquals("email", rejected.field)
        verify(userRepository, never()).save(anyNonNull<UserEntity>())
    }

    @Test
    fun `sign-up rejects a password shorter than the minimum`() {
        `when`(emailConfirmationService.freeUnconfirmedHolder(email = EMAIL, ownerId = null)).thenReturn(true)

        val rejected = assertFailsWith<ResponseStatusException> {
            service.signUp(
                PasswordSignUpRequest(
                    displayName = "Иван",
                    email = EMAIL,
                    login = null,
                    password = "short",
                    deviceInfo = DEVICE,
                )
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, rejected.statusCode)
    }

    @Test
    fun `sign-in accepts a login when the identifier is not an email`() {
        `when`(userRepository.findByLogin(LOGIN)).thenReturn(userEntity())
        `when`(credentialRepository.findById(USER_ID)).thenReturn(Optional.of(credential()))
        givenSessionOpens()

        service.signIn(PasswordSignInRequest(identifier = " Ivan.Trainer ", password = PASSWORD, deviceInfo = DEVICE))

        verify(sessionOpener).openSession(userId = USER_ID, deviceInfo = DEVICE)
    }

    @Test
    fun `a wrong password locks the account after the allowed attempts`() {
        val credential = credential()
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(userEntity())
        `when`(credentialRepository.findById(USER_ID)).thenReturn(Optional.of(credential))

        repeat(MAX_FAILED_ATTEMPTS - 1) {
            assertFailsWith<ResponseStatusException> { service.signIn(signInRequest(password = "wrong-password")) }
        }

        val locked = assertFailsWith<TooManyAttemptsException> {
            service.signIn(signInRequest(password = "wrong-password"))
        }
        assertEquals(LOCK_MINUTES * SECONDS_IN_MINUTE, locked.retryAfterSeconds)
        assertEquals(NOW.plusSeconds(LOCK_MINUTES * SECONDS_IN_MINUTE), credential.lockedUntil)
        verify(sessionOpener, never()).openSession(anyNonNull(), anyNonNull())
    }

    @Test
    fun `a correct password clears earlier failures`() {
        val credential = credential().apply { failedAttempts = MAX_FAILED_ATTEMPTS - 1 }
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(userEntity())
        `when`(credentialRepository.findById(USER_ID)).thenReturn(Optional.of(credential))
        givenSessionOpens()

        service.signIn(signInRequest())

        assertEquals(0, credential.failedAttempts)
        assertNull(credential.lockedUntil)
    }

    @Test
    fun `changing an existing password requires the current one`() {
        val credential = credential()
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(userEntity()))
        `when`(credentialRepository.findById(USER_ID)).thenReturn(Optional.of(credential))

        val rejected = assertFailsWith<ResponseStatusException> {
            service.setPassword(
                userId = USER_ID,
                currentSessionId = SESSION_ID,
                request = SetPasswordRequest(
                    email = null,
                    login = null,
                    currentPassword = "not-the-current-one",
                    newPassword = NEW_PASSWORD,
                ),
            )
        }

        assertEquals(HttpStatus.FORBIDDEN, rejected.statusCode)
        assertTrue(passwordEncoder.matches(PASSWORD, credential.passwordHash))
    }

    @Test
    fun `changing an existing password signs the other devices out`() {
        val credential = credential()
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(userEntity()))
        `when`(credentialRepository.findById(USER_ID)).thenReturn(Optional.of(credential))

        service.setPassword(
            userId = USER_ID,
            currentSessionId = SESSION_ID,
            request = SetPasswordRequest(
                email = null,
                login = null,
                currentPassword = PASSWORD,
                newPassword = NEW_PASSWORD,
            ),
        )

        assertTrue(passwordEncoder.matches(NEW_PASSWORD, credential.passwordHash))
        verify(sessionService).revokeOtherSessions(userId = USER_ID, currentSessionId = SESSION_ID)
    }

    @Test
    fun `the first password needs an email when the account has none`() {
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(userEntity(email = null)))
        `when`(credentialRepository.findById(USER_ID)).thenReturn(Optional.empty())

        val rejected = assertFailsWith<ResponseStatusException> {
            service.setPassword(
                userId = USER_ID,
                currentSessionId = SESSION_ID,
                request = SetPasswordRequest(
                    email = null,
                    login = null,
                    currentPassword = null,
                    newPassword = NEW_PASSWORD,
                ),
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, rejected.statusCode)
        verify(credentialRepository, never()).save(anyNonNull())
    }

    @Test
    fun `the first password adopts the email it was given`() {
        val user = userEntity(email = null, emailConfirmedAt = null)
        `when`(userRepository.findById(USER_ID)).thenReturn(Optional.of(user))
        `when`(credentialRepository.findById(USER_ID)).thenReturn(Optional.empty())
        `when`(emailConfirmationService.freeUnconfirmedHolder(email = EMAIL, ownerId = USER_ID)).thenReturn(true)

        service.setPassword(
            userId = USER_ID,
            currentSessionId = SESSION_ID,
            request = SetPasswordRequest(
                email = " Ivan@Mail.RU ",
                login = null,
                currentPassword = null,
                newPassword = NEW_PASSWORD,
            ),
        )

        assertEquals(EMAIL, user.email)
        assertNull(user.emailConfirmedAt)
        verify(emailConfirmationService).beginQuietly(user = user, email = EMAIL)
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, savedCredential().passwordHash))
    }

    @Test
    fun `each further lockout lasts twice as long, up to the ceiling`() {
        val credential = credential()
        `when`(userRepository.findByEmail(EMAIL)).thenReturn(userEntity())
        `when`(credentialRepository.findById(USER_ID)).thenReturn(Optional.of(credential))

        val lengths = mutableListOf<Long>()
        repeat(4) {
            credential.lockedUntil = null
            repeat(MAX_FAILED_ATTEMPTS) {
                runCatching { service.signIn(signInRequest(password = "wrong-password")) }
            }
            lengths += (credential.lockedUntil?.epochSecond ?: 0) - NOW.epochSecond
        }

        assertEquals(
            listOf(
                LOCK_MINUTES * SECONDS_IN_MINUTE,
                LOCK_MINUTES * 2 * SECONDS_IN_MINUTE,
                LOCK_MINUTES * 4 * SECONDS_IN_MINUTE,
                LOCK_MAX_MINUTES * SECONDS_IN_MINUTE,
            ),
            lengths,
        )
    }

    private fun signInRequest(password: String = PASSWORD) = PasswordSignInRequest(
        identifier = EMAIL,
        password = password,
        deviceInfo = DEVICE,
    )

    private fun credential() = PasswordCredentialEntity(
        userId = USER_ID,
        passwordHash = passwordEncoder.encode(PASSWORD),
        failedAttempts = 0,
        lockedUntil = null,
        lockStreak = 0,
        updatedAt = NOW,
    )

    private fun givenSessionOpens() {
        `when`(sessionOpener.openSession(anyNonNull(), anyNonNull())).thenReturn(openedSession())
    }

    private fun savedUser(): UserEntity {
        val captor = ArgumentCaptor.forClass(UserEntity::class.java)
        verify(userRepository).save(captor.capture())
        return captor.value
    }

    private fun savedCredential(): PasswordCredentialEntity {
        val captor = ArgumentCaptor.forClass(PasswordCredentialEntity::class.java)
        verify(credentialRepository).save(captor.capture())
        return captor.value
    }
}
