package com.notesapp.notes_api.dto

data class BaseResponse<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
)
