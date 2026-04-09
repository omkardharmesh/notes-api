package com.notesapp.notes_api.service

import com.notesapp.notes_api.dto.NoteRequest
import com.notesapp.notes_api.dto.NoteResponse
import com.notesapp.notes_api.mapper.toResponse
import com.notesapp.notes_api.model.Note
import com.notesapp.notes_api.repository.NoteRepository
import com.notesapp.notes_api.repository.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class NoteService(
    private val noteRepository: NoteRepository,
    private val userRepository: UserRepository,
) {
    fun create(userId: Long, noteRequest: NoteRequest): NoteResponse {
        val note = Note(
            title = noteRequest.title,
            content = noteRequest.content,
            color = noteRequest.color,
            createdAt = Instant.now(),
            owner = userRepository.getReferenceById(userId),
        )
        val savedNote = noteRepository.save(
            /* entity = */ note
        )
        return savedNote.toResponse()
    }

    fun update(userId: Long, noteId: Long, noteRequest: NoteRequest): NoteResponse {
        val existingNote: Note = noteRepository.findById(noteId).orElseThrow {
            Exception("Note not found")
        }
        if (userId != existingNote.owner.id) {
            throw Exception("Not owner of this note")
        }

        val updatedNote = noteRepository.save(
            existingNote.copy(
                title = noteRequest.title,
                content = noteRequest.content,
                color = noteRequest.color,
                updatedAt = Instant.now(),
            )
        )

        return updatedNote.toResponse()
    }

    fun getAll(userId: Long): List<NoteResponse> {
        return noteRepository.findByOwnerIdAndIsDeletedFalse(userId).map { it.toResponse() }
    }

    //need to return just succes here ??
    fun delete(noteId: Long, userId: Long): Unit {
        val existingNote = noteRepository.findById(noteId).orElseThrow {
            Exception("Note not found")
        }
        if (userId != existingNote.owner.id) {
            throw Exception("Not owner of this note")
        }

        noteRepository.save(
            existingNote.copy(
                isDeleted = true,
            )
        )
    }
}