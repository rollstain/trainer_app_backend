package app.trainer.backend.owner

import app.trainer.backend.auth.external.ExternalIdentityEntity
import app.trainer.backend.auth.external.ExternalIdentityRepository
import app.trainer.backend.auth.external.ExternalProvider
import app.trainer.backend.auth.password.PasswordCredentialEntity
import app.trainer.backend.auth.password.PasswordCredentialRepository
import app.trainer.backend.auth.password.PasswordStore
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException

private val OWNER_USER_ID: UUID = UUID.fromString("70000000-0000-0000-0000-000000000001")
private val OWNER_COACH_ID: UUID = UUID.fromString("70000000-0000-0000-0000-000000000002")
private val PLAIN_COACH_USER_ID: UUID = UUID.fromString("70000000-0000-0000-0000-000000000003")
private val PLAIN_COACH_ID: UUID = UUID.fromString("70000000-0000-0000-0000-000000000004")
private val NOW: Instant = Instant.parse("2026-08-29T10:00:00Z")
private const val PAGE_SIZE = 2
private const val ROWS_ASKED_FOR_PAGE_OF_TWO = 3

class OwnerCoachServiceTest {

    private val userRepository = mock(UserRepository::class.java)
    private val ownerCoachRepository = mock(OwnerCoachRepository::class.java)
    private val externalIdentityRepository = mock(ExternalIdentityRepository::class.java)
    private val credentialRepository = mock(PasswordCredentialRepository::class.java)
    private val passwordEncoder = mock(PasswordEncoder::class.java)

    private val service = OwnerCoachService(
        userRepository = userRepository,
        ownerCoachRepository = ownerCoachRepository,
        externalIdentityRepository = externalIdentityRepository,
        passwordStore = PasswordStore(
            credentialRepository = credentialRepository,
            passwordEncoder = passwordEncoder,
        ),
    )

    @Test
    fun `a user who is not the owner sees no roster of coaches`() {
        `when`(userRepository.findById(PLAIN_COACH_USER_ID)).thenReturn(
            java.util.Optional.of(user(isOwner = false))
        )

        val refused = assertThrows<ResponseStatusException> {
            service.coaches(ownerUserId = PLAIN_COACH_USER_ID, limit = null, after = null)
        }

        assertEquals(HttpStatus.FORBIDDEN, refused.statusCode)
        verify(ownerCoachRepository, never()).findPage(null, null, PAGE_SIZE)
    }

    @Test
    fun `an unknown user is refused too`() {
        `when`(userRepository.findById(PLAIN_COACH_USER_ID)).thenReturn(java.util.Optional.empty())

        val refused = assertThrows<ResponseStatusException> {
            service.card(ownerUserId = PLAIN_COACH_USER_ID, coachId = OWNER_COACH_ID)
        }

        assertEquals(HttpStatus.FORBIDDEN, refused.statusCode)
    }

    @Test
    fun `the owner gets a page and the cursor points at its last row`() {
        givenOwner()
        `when`(ownerCoachRepository.findPage(null, null, ROWS_ASKED_FOR_PAGE_OF_TWO)).thenReturn(
            listOf(
                row(name = "Вера", createdAt = NOW),
                row(name = "Борис", createdAt = NOW.minusSeconds(60)),
                row(name = "Анна", createdAt = NOW.minusSeconds(120)),
            )
        )

        val page = service.coaches(ownerUserId = OWNER_USER_ID, limit = PAGE_SIZE, after = null)

        assertEquals(listOf("Вера", "Борис"), page.items.map { it.displayName })
        val cursor = decodeCursor(page.nextCursor)
        assertNotNull(cursor, "третья строка была прочитана — значит есть что подкачивать")
        assertEquals(NOW.minusSeconds(60).toString(), cursor.sortKey)
    }

    @Test
    fun `no extra row means no cursor`() {
        givenOwner()
        `when`(ownerCoachRepository.findPage(null, null, ROWS_ASKED_FOR_PAGE_OF_TWO)).thenReturn(
            listOf(row(name = "Вера", createdAt = NOW), row(name = "Борис", createdAt = NOW.minusSeconds(60)))
        )

        val page = service.coaches(ownerUserId = OWNER_USER_ID, limit = PAGE_SIZE, after = null)

        assertEquals(2, page.items.size)
        assertNull(page.nextCursor, "страница неполная — подкачивать нечего")
    }

    @Test
    fun `the card tells how the coach signs in`() {
        givenOwner()
        `when`(ownerCoachRepository.findCard(PLAIN_COACH_ID)).thenReturn(card())
        `when`(credentialRepository.findById(PLAIN_COACH_USER_ID)).thenReturn(
            java.util.Optional.of(credential())
        )
        `when`(externalIdentityRepository.findByUserId(PLAIN_COACH_USER_ID)).thenReturn(
            listOf(identity(ExternalProvider.TELEGRAM))
        )

        val card = service.card(ownerUserId = OWNER_USER_ID, coachId = PLAIN_COACH_ID)

        assertTrue(card.hasPassword)
        assertEquals(listOf("TELEGRAM"), card.providers)
        assertEquals(3, card.activeClients)
        assertEquals(1, card.archivedClients)
    }

    @Test
    fun `an unknown coach is not found`() {
        givenOwner()
        `when`(ownerCoachRepository.findCard(PLAIN_COACH_ID)).thenReturn(null)

        val missing = assertThrows<ResponseStatusException> {
            service.card(ownerUserId = OWNER_USER_ID, coachId = PLAIN_COACH_ID)
        }

        assertEquals(HttpStatus.NOT_FOUND, missing.statusCode)
    }

    private fun givenOwner() {
        `when`(userRepository.findById(OWNER_USER_ID)).thenReturn(java.util.Optional.of(user(isOwner = true)))
    }

    private fun user(isOwner: Boolean): UserEntity = UserEntity(
        id = if (isOwner) OWNER_USER_ID else PLAIN_COACH_USER_ID,
        displayName = if (isOwner) "Владелец" else "Борис",
        phone = null,
        email = null,
        login = null,
        isOwner = isOwner,
        createdAt = NOW,
    )

    private fun credential(): PasswordCredentialEntity = PasswordCredentialEntity(
        userId = PLAIN_COACH_USER_ID,
        passwordHash = "hash",
        failedAttempts = 0,
        lockedUntil = null,
        lockStreak = 0,
        updatedAt = NOW,
    )

    private fun identity(provider: ExternalProvider): ExternalIdentityEntity = ExternalIdentityEntity(
        id = UUID.randomUUID(),
        userId = PLAIN_COACH_USER_ID,
        provider = provider,
        subjectHash = "hash",
        username = "coach",
        createdAt = NOW,
    )

    private fun row(name: String, createdAt: Instant): CoachAccountRow = object : CoachAccountRow {
        override fun getCoachId(): UUID = UUID.nameUUIDFromBytes(name.toByteArray())
        override fun getDisplayName(): String = name
        override fun getCreatedAt(): Instant = createdAt
        override fun getActiveClients(): Long = 0
        override fun getOwner(): Boolean = false
    }

    private fun card(): CoachAccountCardRow = object : CoachAccountCardRow {
        override fun getCoachId(): UUID = PLAIN_COACH_ID
        override fun getUserId(): UUID = PLAIN_COACH_USER_ID
        override fun getDisplayName(): String = "Борис"
        override fun getEmail(): String = "boris@example.com"
        override fun getPhone(): String? = null
        override fun getLogin(): String? = null
        override fun getZoneId(): String = "Europe/Moscow"
        override fun getCreatedAt(): Instant = NOW
        override fun getActiveClients(): Long = 3
        override fun getArchivedClients(): Long = 1
        override fun getLastSeenAt(): Instant = NOW
        override fun getOwner(): Boolean = false
    }
}
