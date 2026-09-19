package com.example.timeuselimiter.data.repository

import com.example.timeuselimiter.data.local.db.dao.UninstallCooldownDao
import com.example.timeuselimiter.data.local.db.entity.UninstallCooldownEntity
import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository

class UninstallProtectionRepositoryImpl(private val dao: UninstallCooldownDao) :
    UninstallProtectionRepository {
    override suspend fun get() = dao.get()
    override suspend fun save(value: UninstallCooldownEntity) = dao.save(value)
    override suspend fun clear() = dao.clear()
}
