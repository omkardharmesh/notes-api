package com.notesapp.notes_api.dto

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
)
