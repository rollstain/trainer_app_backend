package app.trainer.backend.coach

import app.trainer.backend.auth.external.ExternalIdentityEntity
import app.trainer.backend.auth.external.ExternalIdentityRepository
import app.trainer.backend.auth.external.ExternalProvider
import app.trainer.backend.auth.external.VerifiedIdentity
import app.trainer.backend.auth.external.subjectHashOf
import app.trainer.backend.push.PushMessage
import app.trainer.backend.push.PushSender
import app.trainer.backend.push.PushText
import app.trainer.backend.user.UserEntity
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

private val ASKER: UUID = UUID.fromString("92000000-0000-0000-0000-000000000001")
private val OWNER: UUID = UUID.fromString("92000000-0000-0000-0000-000000000002")
private val STRANGER: UUID = UUID.fromString("92000000-0000-0000-0000-000000000003")
private val REQUEST_ID: UUID = UUID.fromString("92000000-0000-0000-0000-000000000004")
private val NOW: Instant = Instant.parse("2026-09-19T09:00:00Z")
private val THREE_DAYS_AGO: Instant = Instant.parse("2026-09-16T09:00:00Z")
private val TWO_WEEKS_AGO: Instant = Instant.parse("2026-09-05T09:00:00Z")
private const val OWNER_TELEGRAM_ID = "700001"
private const val STRANGER_TELEGRAM_ID = "700002"
private const val ZONE = "Europe/Moscow"

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

@Suppress("UNCHECKED_CAST")
private fun <T> capturedBy(captor: ArgumentCaptor<*>): T = captor.capture() as T

class CoachRequestServiceTest {

    private val requestRepository = mock(CoachRequestRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val identityRepository = mock(ExternalIdentityRepository::class.java)
    private val pushSender = mock(PushSender::class.java)

    private val service = CoachRequestService(
        requestRepository = requestRepository,
        userRepository = userRepository,
        coachRepository = coachRepository,
        identityRepository = identityRepository,
        pushSender = pushSender,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `asking to coach files a request instead of granting the role`() {
        givenUser(ASKER)

        service.ask(userId = ASKER, displayName = "  Игорь Ляшук  ", zoneId = ZONE)

        val saved = ArgumentCaptor.forClass(CoachRequestEntity::class.java)
        verify(requestRepository).save(capturedBy<CoachRequestEntity>(saved))
        assertEquals(CoachRequestStatus.PENDING, saved.value.status)
        assertNull(saved.value.announcedAt)
        verify(coachRepository, never()).save(anyNonNull())
    }

    @Test
    fun `asking again while pending keeps one request`() {
        givenUser(ASKER)
        val pending = request(status = CoachRequestStatus.PENDING, decidedAt = null)
        `when`(requestRepository.findByUserId(ASKER)).thenReturn(pending)

        service.ask(userId = ASKER, displayName = "Игорь", zoneId = "Asia/Yekaterinburg")

        verify(requestRepository, never()).save(anyNonNull())
        assertEquals("Asia/Yekaterinburg", pending.zoneId)
        assertEquals(CoachRequestStatus.PENDING, pending.status)
    }

    @Test
    fun `a refusal holds the next request for a week`() {
        givenUser(ASKER)
        `when`(requestRepository.findByUserId(ASKER))
            .thenReturn(request(status = CoachRequestStatus.DECLINED, decidedAt = THREE_DAYS_AGO))

        val failure = assertFailsWith<ResponseStatusException> {
            service.ask(userId = ASKER, displayName = "Игорь", zoneId = ZONE)
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
        assertEquals(LocalDate.parse("2026-09-23"), service.statusOf(ASKER)?.canAskAgainOn)
    }

    @Test
    fun `after the week a refused request opens again and is announced anew`() {
        givenUser(ASKER)
        val declined = request(status = CoachRequestStatus.DECLINED, decidedAt = TWO_WEEKS_AGO)
        declined.announcedAt = TWO_WEEKS_AGO
        `when`(requestRepository.findByUserId(ASKER)).thenReturn(declined)

        service.ask(userId = ASKER, displayName = "Игорь", zoneId = ZONE)

        assertEquals(CoachRequestStatus.PENDING, declined.status)
        assertNull(declined.announcedAt)
        assertNull(declined.decidedAt)
    }

    @Test
    fun `a coach cannot ask to become one`() {
        givenUser(ASKER)
        `when`(coachRepository.findByUserId(ASKER)).thenReturn(coach())

        val failure = assertFailsWith<ResponseStatusException> {
            service.ask(userId = ASKER, displayName = "Игорь", zoneId = ZONE)
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
    }

    @Test
    fun `the owner's approval from telegram makes the asker a coach`() {
        givenUser(ASKER)
        givenTelegramOf(user = OWNER, telegramUserId = OWNER_TELEGRAM_ID, isOwner = true)
        val pending = request(status = CoachRequestStatus.PENDING, decidedAt = null)
        `when`(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pending))

        val decided = service.decide(requestId = REQUEST_ID, approve = true, telegramUserId = OWNER_TELEGRAM_ID)

        assertEquals(CoachRequestStatus.APPROVED, decided.status)
        assertEquals(NOW, pending.decidedAt)
        val created = ArgumentCaptor.forClass(CoachEntity::class.java)
        verify(coachRepository).save(capturedBy<CoachEntity>(created))
        assertEquals(ASKER, created.value.userId)
        assertEquals(ZONE, created.value.zoneId)
    }

    @Test
    fun `a refusal leaves the asker a client`() {
        givenUser(ASKER)
        givenTelegramOf(user = OWNER, telegramUserId = OWNER_TELEGRAM_ID, isOwner = true)
        val pending = request(status = CoachRequestStatus.PENDING, decidedAt = null)
        `when`(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pending))

        val decided = service.decide(requestId = REQUEST_ID, approve = false, telegramUserId = OWNER_TELEGRAM_ID)

        assertEquals(CoachRequestStatus.DECLINED, decided.status)
        verify(coachRepository, never()).save(anyNonNull())
    }

    @Test
    fun `the asker hears the answer, whatever it is`() {
        givenUser(ASKER)
        givenTelegramOf(user = OWNER, telegramUserId = OWNER_TELEGRAM_ID, isOwner = true)
        `when`(requestRepository.findById(REQUEST_ID))
            .thenReturn(Optional.of(request(status = CoachRequestStatus.PENDING, decidedAt = null)))

        service.decide(requestId = REQUEST_ID, approve = true, telegramUserId = OWNER_TELEGRAM_ID)

        val recipients = ArgumentCaptor.forClass(Collection::class.java)
        val message = ArgumentCaptor.forClass(PushMessage::class.java)
        verify(pushSender).send(capturedBy(recipients), capturedBy(message))
        assertEquals(listOf(ASKER), recipients.value.toList())
        assertEquals(PushText.COACH_REQUEST_APPROVED, message.value.text)
    }

    @Test
    fun `a refusal is told too`() {
        givenUser(ASKER)
        givenTelegramOf(user = OWNER, telegramUserId = OWNER_TELEGRAM_ID, isOwner = true)
        `when`(requestRepository.findById(REQUEST_ID))
            .thenReturn(Optional.of(request(status = CoachRequestStatus.PENDING, decidedAt = null)))

        service.decide(requestId = REQUEST_ID, approve = false, telegramUserId = OWNER_TELEGRAM_ID)

        val message = ArgumentCaptor.forClass(PushMessage::class.java)
        verify(pushSender).send(anyNonNull(), capturedBy(message))
        assertEquals(PushText.COACH_REQUEST_DECLINED, message.value.text)
    }

    @Test
    fun `only the owner's telegram decides`() {
        givenTelegramOf(user = STRANGER, telegramUserId = STRANGER_TELEGRAM_ID, isOwner = false)

        val stranger = assertFailsWith<ResponseStatusException> {
            service.decide(requestId = REQUEST_ID, approve = true, telegramUserId = STRANGER_TELEGRAM_ID)
        }
        val unknown = assertFailsWith<ResponseStatusException> {
            service.decide(requestId = REQUEST_ID, approve = true, telegramUserId = "700003")
        }

        assertEquals(HttpStatus.FORBIDDEN, stranger.statusCode)
        assertEquals(HttpStatus.FORBIDDEN, unknown.statusCode)
        verify(coachRepository, never()).save(anyNonNull())
    }

    @Test
    fun `a decided request is not decided twice`() {
        givenTelegramOf(user = OWNER, telegramUserId = OWNER_TELEGRAM_ID, isOwner = true)
        `when`(requestRepository.findById(REQUEST_ID))
            .thenReturn(Optional.of(request(status = CoachRequestStatus.DECLINED, decidedAt = THREE_DAYS_AGO)))

        val failure = assertFailsWith<ResponseStatusException> {
            service.decide(requestId = REQUEST_ID, approve = true, telegramUserId = OWNER_TELEGRAM_ID)
        }

        assertEquals(HttpStatus.CONFLICT, failure.statusCode)
    }

    @Test
    fun `the bot sees who asks and marks the request announced once`() {
        givenUser(ASKER)
        val pending = request(status = CoachRequestStatus.PENDING, decidedAt = null)
        `when`(requestRepository.findByStatusAndAnnouncedAtIsNullOrderByCreatedAtAsc(CoachRequestStatus.PENDING))
            .thenReturn(listOf(pending))
        `when`(requestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pending))

        val announced = service.unannounced().single()
        service.markAnnounced(REQUEST_ID)

        assertEquals("Игорь", announced.displayName)
        assertEquals("igor@example.com", announced.email)
        assertEquals(NOW, pending.announcedAt)
    }

    private fun givenUser(userId: UUID, isOwner: Boolean = false): UserEntity {
        val user = UserEntity(
            id = userId,
            displayName = "Игорь",
            phone = null,
            email = "igor@example.com",
            login = null,
            isOwner = isOwner,
            createdAt = TWO_WEEKS_AGO,
        )
        `when`(userRepository.findById(userId)).thenReturn(Optional.of(user))
        return user
    }

    private fun givenTelegramOf(user: UUID, telegramUserId: String, isOwner: Boolean) {
        givenUser(userId = user, isOwner = isOwner)
        val hash = subjectHashOf(
            VerifiedIdentity(
                provider = ExternalProvider.TELEGRAM,
                subject = telegramUserId,
                displayName = null,
                username = null,
            )
        )
        `when`(identityRepository.findByProviderAndSubjectHash(ExternalProvider.TELEGRAM, hash)).thenReturn(
            ExternalIdentityEntity(
                id = UUID.randomUUID(),
                userId = user,
                provider = ExternalProvider.TELEGRAM,
                subjectHash = hash,
                username = null,
                createdAt = TWO_WEEKS_AGO,
            )
        )
    }

    private fun request(status: CoachRequestStatus, decidedAt: Instant?) = CoachRequestEntity(
        id = REQUEST_ID,
        userId = ASKER,
        zoneId = ZONE,
        status = status,
        createdAt = TWO_WEEKS_AGO,
        announcedAt = null,
        decidedAt = decidedAt,
    )

    private fun coach() = CoachEntity(
        id = UUID.randomUUID(),
        userId = ASKER,
        zoneId = ZONE,
        cancellationWindowHours = 12,
        reminderHour = 10,
        sessionRemindersEnabled = true,
        diaryRemindersEnabled = true,
        checkInRemindersEnabled = true,
        createdAt = TWO_WEEKS_AGO,
    )
}
