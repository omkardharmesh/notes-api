package com.notesapp.notes_api.dto

import java.time.Instant


data class NoteResponse(
    val id: Long,
    val title: String,
    val content: String,
    val color: Long,
    val createdAt: Instant,
)
