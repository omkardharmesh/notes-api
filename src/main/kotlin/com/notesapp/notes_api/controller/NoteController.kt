package com.notesapp.notes_api.controller

import com.notesapp.notes_api.dto.BaseResponse
import com.notesapp.notes_api.dto.NoteRequest
import com.notesapp.notes_api.dto.NoteResponse
import com.notesapp.notes_api.service.NoteService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*


@RestController
@RequestMapping("/notes")
class NoteController(
    val noteService: NoteService
) {
    private fun getUserId(): Long =
        SecurityContextHolder.getContext().authentication!!.principal as Long

    @GetMapping
    fun getNotes(): BaseResponse<List<NoteResponse>> {
        return BaseResponse(
            code = HttpStatus.OK.value(),
            message = "Successfully fetched notes",
            data = noteService.getAll(userId = getUserId())
        )
    }

    @PostMapping
    fun createNote(@RequestBody @Valid noteRequest: NoteRequest): BaseResponse<NoteResponse> {
        val createdNote = noteService.create(getUserId(), noteRequest)
        return BaseResponse(
            code = HttpStatus.CREATED.value(),
            message = "Successfully created note",
            data = createdNote
        )
    }

    @PutMapping("/{id}")
    fun updateNote(
        @PathVariable id: Long,
        @RequestBody @Valid noteRequest: NoteRequest
    ): BaseResponse<NoteResponse> {
        val updatedNote = noteService.update(userId = getUserId(), noteId = id, noteRequest = noteRequest)
        return BaseResponse(
            code = HttpStatus.OK.value(),
            message = "Successfully updated note",
            data = updatedNote
        )
    }

    @DeleteMapping("/{id}")
    fun deleteNote(@PathVariable id: Long): BaseResponse<Unit> {
        noteService.delete(noteId = id, userId = getUserId())
        return BaseResponse(
            code = HttpStatus.OK.value(),
            message = "Successfully deleted note",
        )
    }

}