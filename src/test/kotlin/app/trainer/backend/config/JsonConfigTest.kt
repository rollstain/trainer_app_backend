package app.trainer.backend.config

import app.trainer.backend.schedule.ClientSlotResponse
import app.trainer.backend.traininglog.SaveTrainingLogRequest
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import tools.jackson.databind.json.JsonMapper

private const val NOT_FOUND = 404
private const val SLOT_DURATION_MINUTES = 60
private const val SINGLE_SEAT = 1
private const val SLOT_STARTS_AT_TEXT = "2026-03-03T09:00:00Z"
private val SLOT_STARTS_AT: Instant = Instant.parse(SLOT_STARTS_AT_TEXT)

class JsonConfigTest {

    private val runner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration::class.java))
        .withUserConfiguration(JsonConfig::class.java)

    @Test
    fun `an absent value is left out of the answer instead of being written as null`() {
        runner.run { context ->
            val json = context.getBean(JsonMapper::class.java).writeValueAsString(
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
    }

    @Test
    fun `a slot without a change request or a place in line carries neither`() {
        runner.run { context ->
            val json = context.getBean(JsonMapper::class.java).writeValueAsString(
                ClientSlotResponse(
                    id = UUID.randomUUID(),
                    startsAt = SLOT_STARTS_AT,
                    durationMinutes = SLOT_DURATION_MINUTES,
                    isBookedByMe = true,
                    isAvailable = false,
                    pendingChangeRequestId = null,
                    changeRequest = null,
                    canRequestChange = true,
                    isOnWaitlist = false,
                    waitlistPosition = null,
                    capacity = SINGLE_SEAT,
                    takenSeats = SINGLE_SEAT,
                )
            )

            assertFalse(json.contains("pendingChangeRequestId"), json)
            assertFalse(json.contains("changeRequest"), json)
            assertFalse(json.contains("waitlistPosition"), json)
            assertTrue(json.contains("\"isOnWaitlist\":false"), "ложь — это значение: $json")
            assertTrue(json.contains("\"startsAt\":\"$SLOT_STARTS_AT_TEXT\""), "время — строкой ISO-8601: $json")
        }
    }

    @Test
    fun `a field the server does not know yet does not break the request`() {
        runner.run { context ->
            val request = context.getBean(JsonMapper::class.java).readValue(
                """{"slotId":null,"sets":[],"plannedByCoach":true}""",
                SaveTrainingLogRequest::class.java,
            )

            assertTrue(request.sets.isEmpty())
            assertNull(request.startedAt)
        }
    }
}
