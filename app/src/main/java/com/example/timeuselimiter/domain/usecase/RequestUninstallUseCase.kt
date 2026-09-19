package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.domain.model.UNINSTALL_COOLDOWN_MS
import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository
import com.example.timeuselimiter.domain.time.TimeSource

class RequestUninstallUseCase(
    private val repository: UninstallProtectionRepository,
    clock: TimeSource
) {
    // Encapsulated: the watermark is persisted inside this use case, so callers
    // never touch engine internals.
    private val engine = UninstallCooldownEngine(clock)

    suspend operator fun invoke(): UninstallProtectionState {
        val record = engine.newRecord() ?: return UninstallProtectionState.None
        repository.save(record)
        return UninstallProtectionState.Waiting(
            startedAt = record.startedAtWallMs,
            deadline = record.deadlineWallMs,
            remainingMs = UNINSTALL_COOLDOWN_MS
        )
    }
}
