package com.notesapp.notes_api.controller

import com.notesapp.notes_api.dto.AuthRequest
import com.notesapp.notes_api.dto.AuthResponse
import com.notesapp.notes_api.dto.BaseResponse
import com.notesapp.notes_api.dto.RefreshRequest
import com.notesapp.notes_api.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    fun register(@RequestBody @Valid authRequest: AuthRequest): BaseResponse<AuthResponse> {
        val response = authService.register(authRequest)
        return BaseResponse(
            code = HttpStatus.CREATED.value(),
            message = "Successfully created Account",
            data = response
        )
    }

    @PostMapping("/login")
    fun login(@RequestBody @Valid authRequest: AuthRequest): BaseResponse<AuthResponse> {
        val response = authService.login(authRequest)
        return BaseResponse(
            code = HttpStatus.OK.value(),
            message = "Successfully logged in",
            data = response
        )
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody @Valid refreshRequest: RefreshRequest): BaseResponse<AuthResponse> {
        val response = authService.refresh(refreshRequest.refreshToken, refreshRequest.deviceId)
        return BaseResponse(
            code = HttpStatus.CREATED.value(),
            message = "Successfully Refreshed Token",
            data = response
        )
    }
}

