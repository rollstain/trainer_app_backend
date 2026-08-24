package app.trainer.backend.clientnotes

import app.trainer.backend.config.CurrentUserId
import jakarta.validation.Valid
import java.util.UUID
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/coach")
class ClientNoteController(private val noteService: ClientNoteService) {

    @GetMapping("/clients/{clientUserId}/notes")
    fun notesOfClient(
        @CurrentUserId coachUserId: UUID,
        @PathVariable clientUserId: UUID,
    ): List<ClientNoteResponse> {
        return noteService.notesOfClient(coachUserId = coachUserId, clientUserId = clientUserId)
    }

    @GetMapping("/notes/pinned")
    fun pinnedNotes(@CurrentUserId coachUserId: UUID): List<ClientNoteResponse> {
        return noteService.pinnedNotes(coachUserId = coachUserId)
    }

    @PostMapping("/clients/{clientUserId}/notes")
    fun create(
        @CurrentUserId coachUserId: UUID,
        @PathVariable clientUserId: UUID,
        @Valid @RequestBody request: CreateClientNoteRequest,
    ): ClientNoteResponse {
        return noteService.create(
            coachUserId = coachUserId,
            clientUserId = clientUserId,
            request = request,
        )
    }

    @PutMapping("/notes/{noteId}")
    fun update(
        @CurrentUserId coachUserId: UUID,
        @PathVariable noteId: UUID,
        @Valid @RequestBody request: UpdateClientNoteRequest,
    ): ClientNoteResponse {
        return noteService.update(coachUserId = coachUserId, noteId = noteId, request = request)
    }

    @DeleteMapping("/notes/{noteId}")
    fun archive(@CurrentUserId coachUserId: UUID, @PathVariable noteId: UUID) {
        noteService.archive(coachUserId = coachUserId, noteId = noteId)
    }
}
