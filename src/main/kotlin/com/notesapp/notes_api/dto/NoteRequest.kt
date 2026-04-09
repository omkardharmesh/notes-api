package com.notesapp.notes_api.dto

import jakarta.validation.constraints.NotBlank

data class NoteRequest(
    @NotBlank
    val title: String,
    @NotBlank
    val content: String,
    val color: Long,
)
