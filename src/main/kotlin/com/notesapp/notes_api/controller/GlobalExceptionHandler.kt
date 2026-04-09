package com.notesapp.notes_api.controller

import com.notesapp.notes_api.dto.BaseResponse
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): BaseResponse<List<String>> {
        val errors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        return BaseResponse(
            code = HttpStatus.BAD_REQUEST.value(),
            message = "Validation failed",
            data = errors
        )
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun handleGenericException(ex: Exception): BaseResponse<Nothing> {
        return BaseResponse(
            code = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            message = ex.message ?: "Something went wrong",
        )
    }
}
