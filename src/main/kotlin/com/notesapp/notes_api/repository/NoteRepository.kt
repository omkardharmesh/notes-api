package com.notesapp.notes_api.repository

import com.notesapp.notes_api.model.Note
import org.springframework.data.jpa.repository.JpaRepository

interface NoteRepository : JpaRepository<Note, Long> {
    fun findByOwnerIdAndIsDeletedFalse(ownerId: Long): List<Note>
}