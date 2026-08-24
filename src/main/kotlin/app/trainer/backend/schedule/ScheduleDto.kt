package app.trainer.backend.schedule

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

private const val MAX_SERIES_WEEKS = 52L
private const val MAX_SLOT_DURATION_MINUTES = 600L

data class CreateSlotRequest(
    val startsAt: Instant,
    @field:Positive
    @field:Max(MAX_SLOT_DURATION_MINUTES)
    val durationMinutes: Int,
)

data class CreateSlotSeriesRequest(
    val startDate: LocalDate,
    @field:Positive
    @field:Max(MAX_SERIES_WEEKS)
    val weeksCount: Int,
    @field:Size(min = 1)
    val daysOfWeek: Set<DayOfWeek>,
    val timeOfDay: LocalTime,
    @field:Positive
    @field:Max(MAX_SLOT_DURATION_MINUTES)
    val durationMinutes: Int,
)

data class SkippedSlotResponse(
    val startsAt: Instant,
    val reason: SkipReason,
)

enum class SkipReason { OVERLAPS_EXISTING_SLOT }

data class CreateSlotSeriesResponse(
    val created: List<CoachSlotResponse>,
    val skipped: List<SkippedSlotResponse>,
)

data class AssignSlotRequest(val clientUserId: UUID)

data class CoachSlotResponse(
    val id: UUID,
    val startsAt: Instant,
    val durationMinutes: Int,
    val status: SlotStatus,
    val clientUserId: UUID?,
    val clientDisplayName: String?,
    val pendingChangeRequestId: UUID?,
)

data class ClientSlotResponse(
    val id: UUID,
    val startsAt: Instant,
    val durationMinutes: Int,
    val isBookedByMe: Boolean,
    val isAvailable: Boolean,
    val pendingChangeRequestId: UUID?,
    val canRequestChange: Boolean,
    val isOnWaitlist: Boolean,
)

data class CoachScheduleResponse(
    val coachId: UUID,
    val zoneId: String,
    val slots: List<CoachSlotResponse>,
)

data class ClientScheduleResponse(
    val coachId: UUID,
    val zoneId: String,
    val cancellationWindowHours: Int,
    val slots: List<ClientSlotResponse>,
)

data class SlotChangeRequestBody(
    val kind: SlotChangeKind,
    val proposedStartsAt: Instant?,
)

data class SlotChangeRequestResponse(
    val id: UUID,
    val slotId: UUID,
    val slotStartsAt: Instant,
    val requestedByUserId: UUID,
    val requestedByDisplayName: String?,
    val kind: SlotChangeKind,
    val proposedStartsAt: Instant?,
    val status: SlotChangeStatus,
    val createdAt: Instant,
)

data class ResolveChangeRequestBody(val approve: Boolean)
