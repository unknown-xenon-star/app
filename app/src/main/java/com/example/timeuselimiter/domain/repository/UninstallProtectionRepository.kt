package com.example.timeuselimiter.domain.repository

import com.example.timeuselimiter.data.local.db.entity.UninstallCooldownEntity

interface UninstallProtectionRepository {
    suspend fun get(): UninstallCooldownEntity?
    suspend fun save(value: UninstallCooldownEntity)
    suspend fun clear()
}
