package com.notesapp.notes_api.controller

import com.notesapp.notes_api.dto.BaseResponse
import com.notesapp.notes_api.dto.NoteRequest
import com.notesapp.notes_api.dto.NoteResponse
import com.notesapp.notes_api.service.NoteService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

const val USER_ID = 1L

@RestController
@RequestMapping("/notes")
class NoteController(
    val noteService: NoteService
) {
    @GetMapping
    fun getNotes(): BaseResponse<List<NoteResponse>> {
        return BaseResponse(
            code = HttpStatus.OK.value(),
            message = "Successfully fetched notes",
            data = noteService.getAll(userId = USER_ID)
        )
    }

    @PostMapping
    fun createNote(userId: Long = USER_ID, @RequestBody @Valid noteRequest: NoteRequest): BaseResponse<NoteResponse> {
        val createdNote = noteService.create(userId, noteRequest)
        return BaseResponse(
            code = HttpStatus.CREATED.value(),
            message = "Successfully created note",
            data = createdNote
        )
    }

    @PutMapping("/{id}")
    fun updateNote(
        userId: Long = USER_ID,
        @PathVariable id: Long,
        @RequestBody @Valid noteRequest: NoteRequest
    ): BaseResponse<NoteResponse> {
        val updatedNote = noteService.update(userId = userId, noteId = id, noteRequest = noteRequest)
        return BaseResponse(
            code = HttpStatus.OK.value(),
            message = "Successfully updated note",
            data = updatedNote
        )
    }

    @DeleteMapping("/{id}")
    fun deleteNote(userId: Long = USER_ID, @PathVariable id: Long): BaseResponse<Unit> {

        noteService.delete(noteId = id, userId = userId)
        return BaseResponse(
            code = HttpStatus.OK.value(),
            message = "Successfully deleted note",
        )
    }

}