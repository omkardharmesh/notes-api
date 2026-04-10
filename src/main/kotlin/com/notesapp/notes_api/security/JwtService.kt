package com.notesapp.notes_api.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

@Service
class JwtService(
    @Value("\${app.jwt.secret}") private val secret: String,
    @Value("\${app.jwt.access-token-expiration}") private val accessTokenExpiration: Long,
    @Value("\${app.jwt.refresh-token-expiration}") private val refreshTokenExpiration: Long,
) {
    private val key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))


    fun generateAccessToken(userId: Long, deviceId: String): String {
        val accessToken =
            Jwts.builder()
                .subject(userId.toString())
                .claim("deviceId", deviceId)
                .signWith(key)
                .expiration(Date(System.currentTimeMillis() + accessTokenExpiration))
        return accessToken.compact()
    }

    fun generateRefreshToken(userId: Long, deviceId: String): String {
        val refreshToken =
            Jwts.builder()
                .subject(userId.toString())
                .claim("deviceId", deviceId)
                .signWith(key)
                .expiration(Date(System.currentTimeMillis() + refreshTokenExpiration))
        return refreshToken.compact()
    }

    fun extractUserId(accessToken: String): Long {
        val userId = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(accessToken)
            .payload
        return userId.subject.toLong()
    }

    fun extractDeviceId(accessToken: String): String {
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(accessToken)
            .payload
        return claims.get("deviceId", String::class.java)
    }

    fun getExpirationMs(token: String): Long {
        val expiration = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
            .expiration
        return expiration.time - System.currentTimeMillis()
    }

    fun isTokenValid(accessToken: String): Boolean {
        try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(accessToken)
                .payload
            return true
        } catch (e: Exception) {
            return false
        }

    }
}