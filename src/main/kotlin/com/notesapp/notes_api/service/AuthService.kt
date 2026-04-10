package com.notesapp.notes_api.service

import com.notesapp.notes_api.dto.AuthRequest
import com.notesapp.notes_api.dto.AuthResponse
import com.notesapp.notes_api.model.RefreshToken
import com.notesapp.notes_api.model.User
import com.notesapp.notes_api.exception.EmailAlreadyExistsException
import com.notesapp.notes_api.exception.InvalidCredentialsException
import com.notesapp.notes_api.exception.InvalidTokenException
import com.notesapp.notes_api.exception.ResourceNotFoundException
import com.notesapp.notes_api.repository.RefreshTokenRepository
import com.notesapp.notes_api.repository.UserRepository
import com.notesapp.notes_api.security.JwtService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant

@Service
class AuthService(
    val userRepository: UserRepository,
    val refreshTokenRepository: RefreshTokenRepository,
    val jwtService: JwtService,
    val bCryptPasswordEncoder: BCryptPasswordEncoder,
    @Value("\${app.jwt.refresh-token-expiration}") val refreshTokenExpirationInMillis: Long,
) {
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun register(authRequest: AuthRequest): AuthResponse {
        if (userRepository.findByEmail(authRequest.email) != null) {
            throw EmailAlreadyExistsException()
        }
        val hashedPassword = bCryptPasswordEncoder.encode(authRequest.password)
        val user = User(
            email = authRequest.email,
            username = authRequest.username,
            hashedPassword = hashedPassword!!,
        )
        val savedUser = userRepository.save(user)
        val accessToken = jwtService.generateAccessToken(userId = savedUser.id!!, deviceId = authRequest.deviceId)
        val refreshToken = jwtService.generateRefreshToken(userId = savedUser.id!!, deviceId = authRequest.deviceId)
        refreshTokenRepository.save(
            RefreshToken(
                user = savedUser,
                deviceId = authRequest.deviceId,
                hashedToken = sha256(refreshToken),
                createdAt = Instant.now(),
                expiresAt = Instant.now().plusMillis(refreshTokenExpirationInMillis),
                id = null, // JPA auto-generates it with @GeneratedValue.
            )
        )
        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken
        )
    }

    fun login(authRequest: AuthRequest): AuthResponse {
        val user: User = userRepository.findByEmail(authRequest.email)
            ?: throw InvalidCredentialsException()
        if (bCryptPasswordEncoder.matches(
                authRequest.password,
                user.hashedPassword
            ).not()
        ) {
            throw InvalidCredentialsException()
        }
        val accessToken = jwtService.generateAccessToken(userId = user.id!!, deviceId = authRequest.deviceId)
        val refreshToken = jwtService.generateRefreshToken(userId = user.id!!, deviceId = authRequest.deviceId)
        val existingRefreshToken: RefreshToken? =
            refreshTokenRepository.findByUserAndDeviceId(user, authRequest.deviceId)
        if (existingRefreshToken != null) {
            refreshTokenRepository.save(
                existingRefreshToken.copy(
                    hashedToken = sha256(refreshToken),
                    createdAt = Instant.now(),
                    expiresAt = Instant.now().plusMillis(refreshTokenExpirationInMillis),
                )
            )
        } else {
            refreshTokenRepository.save(
                RefreshToken(
                    user = user,
                    deviceId = authRequest.deviceId,
                    hashedToken = sha256(refreshToken),
                    createdAt = Instant.now(),
                    expiresAt = Instant.now().plusMillis(refreshTokenExpirationInMillis),
                    id = null, // JPA auto-generates it with @GeneratedValue.
                )
            )
        }

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,

            )
    }


    fun refresh(refreshToken: String, deviceId: String): AuthResponse {
        // 1. Extract userId from the token itself
        val userId = jwtService.extractUserId(refreshToken)

        // 2. Find the user — if they've been deleted, reject
        val user = userRepository.findById(userId)
            .orElseThrow { ResourceNotFoundException("User not found") }

        // 3. Find stored refresh token for this user+device
        val storedToken: RefreshToken = refreshTokenRepository.findByUserAndDeviceId(user, deviceId)
            ?: throw InvalidTokenException("No refresh token found for this device")

        // 4. Verify the raw token matches the stored hash
        if (sha256(refreshToken) != storedToken.hashedToken) {
            throw InvalidTokenException("Invalid refresh token")
        }

        // 5. Check if token is expired
        if (storedToken.expiresAt.isBefore(Instant.now())) {
            refreshTokenRepository.delete(storedToken)
            throw InvalidTokenException("Refresh token expired")
        }

        // 6. Generate new token pair
        val newAccessToken = jwtService.generateAccessToken(userId = user.id!!, deviceId = deviceId)
        val newRefreshToken = jwtService.generateRefreshToken(userId = user.id!!, deviceId = deviceId)

        // 7. Rotate — update stored token with new hash + expiry
        refreshTokenRepository.save(
            storedToken.copy(
                hashedToken = sha256(newRefreshToken),
                createdAt = Instant.now(),
                expiresAt = Instant.now().plusMillis(refreshTokenExpirationInMillis),
            )
        )

        // 8. Return new tokens
        return AuthResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
        )
    }
}