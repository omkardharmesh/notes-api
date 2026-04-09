package com.notesapp.notes_api.controller

import com.notesapp.notes_api.dto.BaseResponse
import com.notesapp.notes_api.exception.EmailAlreadyExistsException
import com.notesapp.notes_api.exception.InvalidCredentialsException
import com.notesapp.notes_api.exception.InvalidTokenException
import com.notesapp.notes_api.exception.ResourceNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    /*Client sends POST /auth/login (wrong password)
    → AuthController.login()
      → AuthService.login()
        → bCryptPasswordEncoder.matches() fails
        → throws InvalidCredentialsException    ← exception is born here
      ← exception bubbles up to controller      ← controller doesn't catch it
    ← exception bubbles up to Spring framework
      → GlobalExceptionHandler sees it
      → matches @ExceptionHandler(InvalidCredentialsException::class)
      → returns BaseResponse with code=401
    → Client gets: {"code": 401, "message": "Invalid email or password", "data": null}

  The key: nobody catches the exception manually. It bubbles up through the call stack naturally — service → controller → Spring framework. Then
  @RestControllerAdvice intercepts it.

  @RestControllerAdvice is basically a global try/catch that wraps every controller. You don't write try/catch in your controllers or services — you just
  throw, and the handler catches by exception type.

  The matching is specific-first: if you throw InvalidCredentialsException, Spring checks handlers in order — finds the exact match before it reaches the
  generic Exception handler at the bottom.*/

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

    @ExceptionHandler(InvalidCredentialsException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleInvalidCredentials(ex: InvalidCredentialsException): BaseResponse<Nothing> {
        return BaseResponse(
            code = HttpStatus.UNAUTHORIZED.value(),
            message = ex.message ?: "Invalid credentials",
        )
    }

    @ExceptionHandler(InvalidTokenException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    fun handleInvalidToken(ex: InvalidTokenException): BaseResponse<Nothing> {
        return BaseResponse(
            code = HttpStatus.UNAUTHORIZED.value(),
            message = ex.message ?: "Invalid or expired token",
        )
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleEmailAlreadyExists(ex: EmailAlreadyExistsException): BaseResponse<Nothing> {
        return BaseResponse(
            code = HttpStatus.CONFLICT.value(),
            message = ex.message ?: "Email already exists",
        )
    }

    @ExceptionHandler(ResourceNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleResourceNotFound(ex: ResourceNotFoundException): BaseResponse<Nothing> {
        return BaseResponse(
            code = HttpStatus.NOT_FOUND.value(),
            message = ex.message ?: "Resource not found",
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
