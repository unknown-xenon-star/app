package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository
import com.example.timeuselimiter.domain.time.TimeSource

class GetUninstallProtectionStateUseCase(
    private val repository: UninstallProtectionRepository,
    clock: TimeSource
) {
    private val engine = UninstallCooldownEngine(clock)

    suspend operator fun invoke(): UninstallProtectionState {
        val record = repository.get()
        val state = engine.state(record)
        engine.advancedObservation?.let { repository.save(it) }
        // A lapsed decision window ends the cycle: the user must start a fresh cooldown.
        if (state is UninstallProtectionState.None && record != null) repository.clear()
        return state
    }
}
