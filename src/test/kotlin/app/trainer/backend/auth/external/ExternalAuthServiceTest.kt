package app.trainer.backend.auth.external

import app.trainer.backend.auth.AuthTokensResponse
import app.trainer.backend.auth.SessionOpener
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val KNOWN_USER_ID: UUID = UUID.fromString("b0000000-0000-0000-0000-000000000001")
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private const val YANDEX_TOKEN = "yandex-token"
private const val YANDEX_SUBJECT = "yandex-subject-42"
private const val DEVICE = "Pixel 8"

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class ExternalAuthServiceTest {

    private val identityRepository = mock(ExternalIdentityRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val sessionOpener = mock(SessionOpener::class.java)
    private val yandex = mock(ExternalIdentityVerifier::class.java)

    private val service: ExternalAuthService by lazy {
        `when`(yandex.provider).thenReturn(ExternalProvider.YANDEX)
        ExternalAuthService(
            identityRepository = identityRepository,
            userRepository = userRepository,
            sessionOpener = sessionOpener,
            verifiers = listOf(yandex),
            clock = Clock.fixed(NOW, ZoneOffset.UTC),
        )
    }

    @Test
    fun `a known account signs in without creating a second profile`() {
        givenVerifiedYandexUser()
        `when`(identityRepository.findByProviderAndSubjectHash(anyNonNull(), anyNonNull()))
            .thenReturn(identity(KNOWN_USER_ID))
        givenSessionOpens()

        service.signIn(signInRequest())

        verify(sessionOpener).openSession(userId = KNOWN_USER_ID, deviceInfo = DEVICE)
        verify(userRepository, never()).save(anyNonNull<UserEntity>())
    }

    @Test
    fun `a first-time account gets a profile and keeps the name from the provider`() {
        givenVerifiedYandexUser()
        `when`(identityRepository.findByProviderAndSubjectHash(anyNonNull(), anyNonNull())).thenReturn(null)
        `when`(userRepository.save(anyNonNull<UserEntity>())).thenAnswer { it.arguments.first() as UserEntity }
        givenSessionOpens()

        service.signIn(signInRequest())

        val saved = org.mockito.ArgumentCaptor.forClass(UserEntity::class.java)
        verify(userRepository).save(saved.capture())
        assertEquals("Анна Петрова", saved.value.displayName)
        verify(identityRepository).save(anyNonNull())
    }

    @Test
    fun `an account already tied to someone else is refused`() {
        givenVerifiedYandexUser()
        `when`(identityRepository.findByProviderAndSubjectHash(anyNonNull(), anyNonNull()))
            .thenReturn(identity(UUID.randomUUID()))

        val failure = assertFailsWith<ResponseStatusException> {
            service.link(userId = KNOWN_USER_ID, request = LinkIdentityRequest(ExternalProvider.YANDEX, YANDEX_TOKEN))
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
    }

    @Test
    fun `linking the same account twice changes nothing`() {
        givenVerifiedYandexUser()
        `when`(identityRepository.findByProviderAndSubjectHash(anyNonNull(), anyNonNull()))
            .thenReturn(identity(KNOWN_USER_ID))
        `when`(identityRepository.findByUserId(KNOWN_USER_ID)).thenReturn(listOf(identity(KNOWN_USER_ID)))

        val linked = service.link(
            userId = KNOWN_USER_ID,
            request = LinkIdentityRequest(ExternalProvider.YANDEX, YANDEX_TOKEN),
        )

        assertEquals(listOf(ExternalProvider.YANDEX), linked.map { it.provider })
        verify(identityRepository, never()).save(anyNonNull())
    }

    @Test
    fun `the only way in cannot be unlinked`() {
        `when`(identityRepository.findByUserId(KNOWN_USER_ID)).thenReturn(listOf(identity(KNOWN_USER_ID)))

        val failure = assertFailsWith<ResponseStatusException> {
            service.unlink(userId = KNOWN_USER_ID, provider = ExternalProvider.YANDEX)
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
        verify(identityRepository, never()).delete(anyNonNull())
    }

    @Test
    fun `a spare way in can be unlinked`() {
        val yandexIdentity = identity(KNOWN_USER_ID)
        val vkIdentity = identity(KNOWN_USER_ID, provider = ExternalProvider.VK)
        `when`(identityRepository.findByUserId(KNOWN_USER_ID)).thenReturn(listOf(yandexIdentity, vkIdentity))

        service.unlink(userId = KNOWN_USER_ID, provider = ExternalProvider.VK)

        verify(identityRepository).delete(vkIdentity)
    }

    @Test
    fun `a provider nobody configured is not a way in`() {
        val failure = assertFailsWith<ResponseStatusException> {
            service.signIn(
                ExternalSignInRequest(provider = ExternalProvider.APPLE, token = "apple", deviceInfo = DEVICE)
            )
        }

        assertEquals(HttpStatus.NOT_IMPLEMENTED, failure.statusCode)
    }

    @Test
    fun `the provider subject never reaches the database as is`() {
        givenVerifiedYandexUser()
        `when`(identityRepository.findByProviderAndSubjectHash(anyNonNull(), anyNonNull())).thenReturn(null)
        `when`(userRepository.save(anyNonNull<UserEntity>())).thenAnswer { it.arguments.first() as UserEntity }
        givenSessionOpens()

        service.signIn(signInRequest())

        val saved = org.mockito.ArgumentCaptor.forClass(ExternalIdentityEntity::class.java)
        verify(identityRepository).save(saved.capture())
        assertTrue(!saved.value.subjectHash.contains(YANDEX_SUBJECT), "идентификатор провайдера хранится хешем")
    }

    private fun signInRequest() = ExternalSignInRequest(
        provider = ExternalProvider.YANDEX,
        token = YANDEX_TOKEN,
        deviceInfo = DEVICE,
    )

    private fun givenVerifiedYandexUser() {
        `when`(yandex.verify(YANDEX_TOKEN)).thenReturn(
            VerifiedIdentity(
                provider = ExternalProvider.YANDEX,
                subject = YANDEX_SUBJECT,
                displayName = "Анна Петрова",
            )
        )
    }

    private fun givenSessionOpens() {
        `when`(sessionOpener.openSession(anyNonNull(), anyNonNull())).thenReturn(
            AuthTokensResponse(accessToken = "access", refreshToken = "refresh", accessTokenExpiresAt = NOW)
        )
    }

    private fun identity(
        userId: UUID,
        provider: ExternalProvider = ExternalProvider.YANDEX,
    ): ExternalIdentityEntity = ExternalIdentityEntity(
        id = UUID.randomUUID(),
        userId = userId,
        provider = provider,
        subjectHash = "hash",
        createdAt = NOW,
    )
}
