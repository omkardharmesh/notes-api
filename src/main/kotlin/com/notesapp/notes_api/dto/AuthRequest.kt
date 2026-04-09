package com.notesapp.notes_api.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class AuthRequest(
    @NotBlank
    val username: String,
    @NotBlank
    val password: String,
    @NotBlank @Email
    val email: String,
    @NotBlank
    val deviceId: String,
)
