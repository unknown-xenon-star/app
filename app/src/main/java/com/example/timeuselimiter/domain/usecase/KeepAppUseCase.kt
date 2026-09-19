package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository

class KeepAppUseCase(private val repository: UninstallProtectionRepository) {
    suspend operator fun invoke() = repository.clear()
}
