package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository
import com.example.timeuselimiter.domain.time.TimeSource

class ContinueUninstallUseCase(
    private val repository: UninstallProtectionRepository,
    clock: TimeSource
) {
    private val request = RequestUninstallUseCase(repository, clock)
    suspend operator fun invoke(current: UninstallProtectionState): UninstallProtectionState {
        if (current !is UninstallProtectionState.Confirmation) return current
        return request()
    }
}
