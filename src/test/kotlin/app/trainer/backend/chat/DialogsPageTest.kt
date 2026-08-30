package app.trainer.backend.chat

import app.trainer.backend.coach.CoachRepository
import app.trainer.backend.config.PageCursor
import app.trainer.backend.config.decodeCursor
import app.trainer.backend.config.encodeCursor
import app.trainer.backend.media.MediaFileService
import app.trainer.backend.user.UserRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.context.ApplicationEventPublisher

private val VIEWER_USER_ID: UUID = UUID.fromString("90000000-0000-0000-0000-000000000001")
private val COACH_ID: UUID = UUID.fromString("90000000-0000-0000-0000-000000000002")
private val CLIENT_USER_ID: UUID = UUID.fromString("90000000-0000-0000-0000-000000000003")
private val FIRST_DIALOG_ID: UUID = UUID.fromString("90000000-0000-0000-0000-000000000004")
private val SECOND_DIALOG_ID: UUID = UUID.fromString("90000000-0000-0000-0000-000000000005")
private val THIRD_DIALOG_ID: UUID = UUID.fromString("90000000-0000-0000-0000-000000000006")
private const val LAST_MESSAGE_BODY = "Готова к завтрашней"
private val NOW: Instant = Instant.parse("2026-03-02T09:00:00Z")
private val EARLIER: Instant = Instant.parse("2026-03-01T09:00:00Z")
private const val PAGE_SIZE = 2
private const val PAGE_SIZE_WITH_PROBE = PAGE_SIZE + 1
private const val LAST_MESSAGE_SEQ = 7L
private const val READ_SEQ = 5L
private const val PEER_READ_SEQ = 6L
private const val UNREAD_COUNT = 2L

class DialogsPageTest {

    private val dialogRepository = mock(DialogRepository::class.java)
    private val messageRepository = mock(MessageRepository::class.java)
    private val dialogReadRepository = mock(DialogReadRepository::class.java)
    private val coachRepository = mock(CoachRepository::class.java)
    private val userRepository = mock(UserRepository::class.java)
    private val mediaFileService = mock(MediaFileService::class.java)
    private val eventPublisher = mock(ApplicationEventPublisher::class.java)

    private val service = ChatService(
        dialogRepository = dialogRepository,
        messageRepository = messageRepository,
        dialogReadRepository = dialogReadRepository,
        coachRepository = coachRepository,
        userRepository = userRepository,
        mediaFileService = mediaFileService,
        eventPublisher = eventPublisher,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    @Test
    fun `a full page of dialogs points at the rest with a cursor`() {
        `when`(dialogRepository.findPage(VIEWER_USER_ID, null, null, PAGE_SIZE_WITH_PROBE)).thenReturn(
            listOf(
                row(dialogId = FIRST_DIALOG_ID, sortKey = NOW),
                row(dialogId = SECOND_DIALOG_ID, sortKey = EARLIER),
                row(dialogId = THIRD_DIALOG_ID, sortKey = EARLIER),
            )
        )

        val page = service.dialogsOf(userId = VIEWER_USER_ID, limit = PAGE_SIZE, after = null)

        assertEquals(listOf(FIRST_DIALOG_ID, SECOND_DIALOG_ID), page.items.map { it.id })
        assertEquals(
            PageCursor(sortKey = EARLIER.toString(), id = SECOND_DIALOG_ID),
            decodeCursor(page.nextCursor),
        )
    }

    @Test
    fun `the last page of dialogs has no continuation`() {
        `when`(dialogRepository.findPage(VIEWER_USER_ID, EARLIER.toString(), SECOND_DIALOG_ID, PAGE_SIZE_WITH_PROBE))
            .thenReturn(listOf(row(dialogId = THIRD_DIALOG_ID, sortKey = EARLIER)))

        val page = service.dialogsOf(
            userId = VIEWER_USER_ID,
            limit = PAGE_SIZE,
            after = encodeCursor(PageCursor(sortKey = EARLIER.toString(), id = SECOND_DIALOG_ID)),
        )

        assertEquals(listOf(THIRD_DIALOG_ID), page.items.map { it.id })
        assertNull(page.nextCursor)
    }

    @Test
    fun `a dialog nobody wrote in yet comes without a last message`() {
        `when`(dialogRepository.findPage(VIEWER_USER_ID, null, null, PAGE_SIZE_WITH_PROBE))
            .thenReturn(listOf(row(dialogId = FIRST_DIALOG_ID, sortKey = Instant.EPOCH, hasMessage = false)))

        val page = service.dialogsOf(userId = VIEWER_USER_ID, limit = PAGE_SIZE, after = null)

        assertNull(page.items.single().lastMessagePreview)
        assertNull(page.items.single().lastMessageAt)
        assertEquals(UNREAD_COUNT, page.items.single().unreadCount)
    }

    private fun row(dialogId: UUID, sortKey: Instant, hasMessage: Boolean = true): DialogListRow = Row(
        dialogId = dialogId,
        sortKey = sortKey,
        messageCreatedAt = sortKey.takeIf { hasMessage },
    )
}

private class Row(
    private val dialogId: UUID,
    private val sortKey: Instant,
    private val messageCreatedAt: Instant?,
) : DialogListRow {

    override fun getDialogId(): UUID = dialogId

    override fun getCoachId(): UUID = COACH_ID

    override fun getClientUserId(): UUID = CLIENT_USER_ID

    override fun getPeerUserId(): UUID = CLIENT_USER_ID

    override fun getPeerDisplayName(): String = "Анна"

    override fun getLastMessageSeq(): Long = LAST_MESSAGE_SEQ

    override fun getReadSeq(): Long = READ_SEQ

    override fun getPeerReadSeq(): Long = PEER_READ_SEQ

    override fun getUnreadCount(): Long = UNREAD_COUNT

    override fun getSortKey(): Instant = sortKey

    override fun getMessageBody(): String? = LAST_MESSAGE_BODY.takeIf { messageCreatedAt != null }

    override fun getMessageCreatedAt(): Instant? = messageCreatedAt
}
