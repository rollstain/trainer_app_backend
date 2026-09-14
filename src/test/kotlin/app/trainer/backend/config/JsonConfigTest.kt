package app.trainer.backend.config

import app.trainer.backend.schedule.ClientSlotResponse
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

private const val NOT_FOUND = 404
private const val SLOT_DURATION_MINUTES = 60
private const val SINGLE_SEAT = 1
private val SLOT_STARTS_AT: Instant = Instant.parse("2026-03-03T09:00:00Z")

class JsonConfigTest {

    private val mapper: ObjectMapper = Jackson2ObjectMapperBuilder.json()
        .also { builder -> JsonConfig().absentValuesOmitted().customize(builder) }
        .build()

    @Test
    fun `an absent value is left out of the answer instead of being written as null`() {
        val json = mapper.writeValueAsString(
            ApiErrorResponse(
                status = NOT_FOUND,
                message = "Приглашение не найдено",
                fieldErrors = emptyMap(),
                retryAfterSeconds = null,
            )
        )

        assertFalse(json.contains("retryAfterSeconds"), json)
        assertTrue(json.contains("fieldErrors"), "пустая коллекция — это значение, а не его отсутствие: $json")
    }

    @Test
    fun `a slot without a change request or a place in line carries neither`() {
        val json = mapper.writeValueAsString(
            ClientSlotResponse(
                id = UUID.randomUUID(),
                startsAt = SLOT_STARTS_AT,
                durationMinutes = SLOT_DURATION_MINUTES,
                isBookedByMe = true,
                isAvailable = false,
                pendingChangeRequestId = null,
                canRequestChange = true,
                isOnWaitlist = false,
                waitlistPosition = null,
                capacity = SINGLE_SEAT,
                takenSeats = SINGLE_SEAT,
            )
        )

        assertFalse(json.contains("pendingChangeRequestId"), json)
        assertFalse(json.contains("waitlistPosition"), json)
        assertTrue(json.contains("isOnWaitlist"), "ложь — это значение: $json")
    }
}
