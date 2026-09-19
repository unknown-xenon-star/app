package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository
import com.example.timeuselimiter.domain.time.TimeSource

class RequestUninstallUseCase(
    private val repository: UninstallProtectionRepository,
    clock: TimeSource
) {
    private val engine = UninstallCooldownEngine(clock)

    suspend operator fun invoke(): UninstallProtectionState {
        val record = engine.newRecord() ?: return UninstallProtectionState.None
        repository.save(record)
        return engine.state(record)
    }
}
