package app.trainer.backend.coach

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.util.UUID

private const val MIN_CANCELLATION_WINDOW_HOURS = 0L
private const val MAX_CANCELLATION_WINDOW_HOURS = 168L
private const val MIN_REMINDER_HOUR = 0L
private const val MAX_REMINDER_HOUR = 23L

data class CoachClientResponse(
    val userId: UUID,
    val displayName: String,
    val status: CoachClientStatus,
    val hasMedicalNotes: Boolean,
    val linkedAt: Instant,
)

data class CoachSummaryResponse(
    val coachId: UUID,
    val userId: UUID,
    val displayName: String,
    val zoneId: String,
    val cancellationWindowHours: Int,
)

data class UpdateCoachPolicyRequest(
    @field:Min(MIN_CANCELLATION_WINDOW_HOURS)
    @field:Max(MAX_CANCELLATION_WINDOW_HOURS)
    val cancellationWindowHours: Int?,
    @field:Min(MIN_REMINDER_HOUR)
    @field:Max(MAX_REMINDER_HOUR)
    val reminderHour: Int?,
    val sessionRemindersEnabled: Boolean?,
    val diaryRemindersEnabled: Boolean?,
    val checkInRemindersEnabled: Boolean?,
)

data class CoachPolicyResponse(
    val cancellationWindowHours: Int,
    val reminderHour: Int,
    val sessionRemindersEnabled: Boolean,
    val diaryRemindersEnabled: Boolean,
    val checkInRemindersEnabled: Boolean,
)
