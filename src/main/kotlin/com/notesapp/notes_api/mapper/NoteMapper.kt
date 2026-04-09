package com.notesapp.notes_api.mapper

import com.notesapp.notes_api.dto.NoteResponse
import com.notesapp.notes_api.model.Note

fun Note.toResponse() = NoteResponse(
    id = id!!,
    title = title,
    content = content,
    color = color,
    createdAt = createdAt,
)
