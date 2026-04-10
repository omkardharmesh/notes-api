package com.notesapp.notes_api.service

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class TokenBlacklistService(
    private val redisTemplate: StringRedisTemplate,
) {
    fun blacklist(token: String, expirationMs: Long) {
        redisTemplate.opsForValue().set(
            "blacklist:$token",
            "true",
            expirationMs,
            TimeUnit.MILLISECONDS,
        )
    }

    fun isBlacklisted(token: String): Boolean {
        return redisTemplate.hasKey("blacklist:$token")
    }
}
