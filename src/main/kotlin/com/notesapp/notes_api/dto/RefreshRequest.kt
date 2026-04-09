package com.notesapp.notes_api.dto

data class RefreshRequest(val refreshToken: String, val deviceId: String)
