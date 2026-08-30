package app.trainer.backend.program

import app.trainer.backend.config.CurrentUserId
import app.trainer.backend.config.pageResponse
import jakarta.validation.Valid
import java.time.LocalDate
import java.util.UUID
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class ProgramController(private val programService: ProgramService) {

    @GetMapping("/coach/programs")
    fun programs(
        @CurrentUserId coachUserId: UUID,
        @RequestParam(required = false) limit: Int?,
        @RequestParam(required = false) after: String?,
    ): ResponseEntity<List<ProgramSummaryResponse>> {
        return pageResponse(programService.programsOf(coachUserId = coachUserId, limit = limit, after = after))
    }

    @PostMapping("/coach/programs")
    fun create(
        @CurrentUserId coachUserId: UUID,
        @Valid @RequestBody request: CreateProgramRequest,
    ): ProgramResponse {
        return programService.create(coachUserId = coachUserId, request = request)
    }

    @GetMapping("/coach/programs/{programId}")
    fun program(@CurrentUserId coachUserId: UUID, @PathVariable programId: UUID): ProgramResponse {
        return programService.programOf(coachUserId = coachUserId, programId = programId)
    }

    @PostMapping("/coach/programs/{programId}/duplicate")
    fun duplicate(
        @CurrentUserId coachUserId: UUID,
        @PathVariable programId: UUID,
        @Valid @RequestBody request: DuplicateProgramRequest,
    ): ProgramResponse {
        return programService.duplicate(
            coachUserId = coachUserId,
            programId = programId,
            request = request,
        )
    }

    @PutMapping("/coach/programs/{programId}/days")
    fun saveDay(
        @CurrentUserId coachUserId: UUID,
        @PathVariable programId: UUID,
        @Valid @RequestBody request: SaveProgramDayRequest,
    ): ProgramResponse {
        return programService.saveDay(coachUserId = coachUserId, programId = programId, request = request)
    }

    @DeleteMapping("/coach/programs/{programId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun archive(@CurrentUserId coachUserId: UUID, @PathVariable programId: UUID) {
        programService.archive(coachUserId = coachUserId, programId = programId)
    }

    @PostMapping("/coach/programs/{programId}/assign")
    fun assign(
        @CurrentUserId coachUserId: UUID,
        @PathVariable programId: UUID,
        @Valid @RequestBody request: AssignProgramRequest,
    ): ClientProgramResponse {
        return programService.assign(coachUserId = coachUserId, programId = programId, request = request)
    }

    @GetMapping("/coach/clients/{clientUserId}/program")
    fun clientProgram(
        @CurrentUserId coachUserId: UUID,
        @PathVariable clientUserId: UUID,
    ): ClientProgramStateResponse {
        return ClientProgramStateResponse(
            program = programService.clientProgram(coachUserId = coachUserId, clientUserId = clientUserId)
        )
    }

    @DeleteMapping("/coach/clients/{clientUserId}/program")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun endAssignment(@CurrentUserId coachUserId: UUID, @PathVariable clientUserId: UUID) {
        programService.endAssignment(coachUserId = coachUserId, clientUserId = clientUserId)
    }

    @GetMapping("/me/program")
    fun ownProgram(@CurrentUserId userId: UUID): ClientProgramStateResponse {
        return ClientProgramStateResponse(program = programService.ownProgram(userId = userId))
    }

    @GetMapping("/me/program/planned")
    fun planned(
        @CurrentUserId userId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<PlannedWorkoutResponse> {
        return programService.plannedWorkouts(userId = userId, from = from, to = to)
    }
}
