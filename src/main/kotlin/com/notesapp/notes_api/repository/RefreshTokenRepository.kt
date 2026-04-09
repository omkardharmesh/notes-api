package com.notesapp.notes_api.repository;

import com.notesapp.notes_api.model.RefreshToken
import com.notesapp.notes_api.model.User
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {
    fun findByUserAndDeviceId(user: User, deviceId: String): RefreshToken?
    fun deleteByUser(user: User)
}
