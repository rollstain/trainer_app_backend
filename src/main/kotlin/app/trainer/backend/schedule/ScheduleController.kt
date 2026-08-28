package app.trainer.backend.schedule

import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.time.Instant
import java.util.UUID
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/schedule")
class ScheduleController(private val scheduleService: ScheduleService) {

    @PostMapping("/slots")
    fun createSlot(
        @CurrentUserId coachUserId: UUID,
        @Valid @RequestBody request: CreateSlotRequest,
    ): CoachSlotResponse {
        return scheduleService.createSlot(coachUserId = coachUserId, request = request)
    }

    @PostMapping("/slots/series")
    fun createSlotSeries(
        @CurrentUserId coachUserId: UUID,
        @Valid @RequestBody request: CreateSlotSeriesRequest,
    ): CreateSlotSeriesResponse {
        return scheduleService.createSlotSeries(coachUserId = coachUserId, request = request)
    }

    @GetMapping("/coach")
    fun coachSchedule(
        @CurrentUserId coachUserId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant,
    ): CoachScheduleResponse {
        return scheduleService.coachSchedule(coachUserId = coachUserId, from = from, to = to)
    }

    @GetMapping("/coaches/{coachId}")
    fun clientSchedule(
        @CurrentUserId userId: UUID,
        @PathVariable coachId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant,
    ): ClientScheduleResponse {
        return scheduleService.clientSchedule(userId = userId, coachId = coachId, from = from, to = to)
    }

    @GetMapping("/slots/{slotId}")
    fun coachSlot(@CurrentUserId coachUserId: UUID, @PathVariable slotId: UUID): CoachSlotResponse {
        return scheduleService.coachSlot(coachUserId = coachUserId, slotId = slotId)
    }

    @PostMapping("/slots/{slotId}/assign")
    fun assignSlot(
        @CurrentUserId coachUserId: UUID,
        @PathVariable slotId: UUID,
        @RequestBody request: AssignSlotRequest,
    ): CoachSlotResponse {
        return scheduleService.assignSlot(
            coachUserId = coachUserId,
            slotId = slotId,
            clientUserId = request.clientUserId,
        )
    }

    @DeleteMapping("/slots/{slotId}/participants/{clientUserId}")
    fun removeParticipant(
        @CurrentUserId coachUserId: UUID,
        @PathVariable slotId: UUID,
        @PathVariable clientUserId: UUID,
    ): CoachSlotResponse {
        return scheduleService.removeParticipant(
            coachUserId = coachUserId,
            slotId = slotId,
            clientUserId = clientUserId,
        )
    }

    @PostMapping("/slots/{slotId}/cancel")
    fun cancelSlot(@CurrentUserId coachUserId: UUID, @PathVariable slotId: UUID): CoachSlotResponse {
        return scheduleService.cancelSlot(coachUserId = coachUserId, slotId = slotId)
    }

    @PostMapping("/slots/{slotId}/complete")
    fun completeSlot(@CurrentUserId coachUserId: UUID, @PathVariable slotId: UUID): CoachSlotResponse {
        return scheduleService.completeSlot(coachUserId = coachUserId, slotId = slotId)
    }

    @PostMapping("/slots/{slotId}/book")
    fun book(@CurrentUserId userId: UUID, @PathVariable slotId: UUID): ClientSlotResponse {
        return scheduleService.book(userId = userId, slotId = slotId)
    }

    @PostMapping("/slots/{slotId}/waitlist")
    fun joinWaitlist(@CurrentUserId userId: UUID, @PathVariable slotId: UUID): ClientSlotResponse {
        return scheduleService.joinWaitlist(userId = userId, slotId = slotId)
    }

    @DeleteMapping("/slots/{slotId}/waitlist")
    fun leaveWaitlist(@CurrentUserId userId: UUID, @PathVariable slotId: UUID): ClientSlotResponse {
        return scheduleService.leaveWaitlist(userId = userId, slotId = slotId)
    }

    @PostMapping("/slots/{slotId}/change-requests")
    fun requestChange(
        @CurrentUserId userId: UUID,
        @PathVariable slotId: UUID,
        @RequestBody body: SlotChangeRequestBody,
    ): SlotChangeRequestResponse {
        return scheduleService.requestChange(userId = userId, slotId = slotId, body = body)
    }

    @GetMapping("/change-requests/pending")
    fun pendingChangeRequests(@CurrentUserId coachUserId: UUID): List<SlotChangeRequestResponse> {
        return scheduleService.pendingChangeRequests(coachUserId = coachUserId)
    }

    @PostMapping("/change-requests/{requestId}/resolve")
    fun resolveChange(
        @CurrentUserId coachUserId: UUID,
        @PathVariable requestId: UUID,
        @RequestBody body: ResolveChangeRequestBody,
    ): SlotChangeRequestResponse {
        return scheduleService.resolveChange(
            coachUserId = coachUserId,
            requestId = requestId,
            approve = body.approve,
        )
    }
}
