package app.trainer.backend.schedule

import app.trainer.backend.clientnotes.ClientNoteRepository
import app.trainer.backend.user.UserRepository
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private val SLOT_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000001")
private val OTHER_SLOT_ID: UUID = UUID.fromString("91000000-0000-0000-0000-000000000002")
private val FIRST_IN_LINE: UUID = UUID.fromString("91000000-0000-0000-0000-000000000003")
private val SECOND_IN_LINE: UUID = UUID.fromString("91000000-0000-0000-0000-000000000004")
private val JOINED_AT: Instant = Instant.parse("2026-03-02T09:00:00Z")

@Suppress("UNCHECKED_CAST")
private fun <T> anyNonNull(): T = ArgumentMatchers.any<T>() ?: (null as T)

class SlotRosterTest {

    private val waitlistRepository = mock(SlotWaitlistRepository::class.java)

    private val roster = SlotRoster(
        participantRepository = mock(SlotParticipantRepository::class.java),
        waitlistRepository = waitlistRepository,
        clientNoteRepository = mock(ClientNoteRepository::class.java),
        userRepository = mock(UserRepository::class.java),
    )

    @Test
    fun `the place in line counts everyone who joined earlier`() {
        `when`(waitlistRepository.findBySlotIdInAndUserId(anyNonNull(), anyNonNull()))
            .thenReturn(listOf(waiting(SLOT_ID, SECOND_IN_LINE)))
        `when`(waitlistRepository.findBySlotIdInOrderByCreatedAtAsc(anyNonNull()))
            .thenReturn(listOf(waiting(SLOT_ID, FIRST_IN_LINE), waiting(SLOT_ID, SECOND_IN_LINE)))

        val positions = roster.waitlistPositionsOf(
            userId = SECOND_IN_LINE,
            slotIds = listOf(SLOT_ID, OTHER_SLOT_ID),
        )

        assertEquals(mapOf(SLOT_ID to 2), positions)
    }

    @Test
    fun `someone outside every waitlist has no places and costs no second query`() {
        `when`(waitlistRepository.findBySlotIdInAndUserId(anyNonNull(), anyNonNull())).thenReturn(emptyList())

        val positions = roster.waitlistPositionsOf(userId = FIRST_IN_LINE, slotIds = listOf(SLOT_ID))

        assertEquals(emptyMap<UUID, Int>(), positions)
        verify(waitlistRepository, never()).findBySlotIdInOrderByCreatedAtAsc(anyNonNull())
    }

    private fun waiting(slotId: UUID, userId: UUID): SlotWaitlistEntity = SlotWaitlistEntity(
        id = UUID.randomUUID(),
        slotId = slotId,
        userId = userId,
        createdAt = JOINED_AT,
        notifiedAt = null,
    )
}
