package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository
import com.example.timeuselimiter.domain.time.TimeSource

sealed interface UninstallDecision {
    /** Emitted exactly once when a persisted confirmation window is spent on uninstalling. */
    data object Confirmed : UninstallDecision

    /** Emitted when the user chooses to keep the app, or no live window exists. */
    data object Cancelled : UninstallDecision
}

class ContinueUninstallUseCase(
    private val repository: UninstallProtectionRepository,
    clock: TimeSource
) {
    private val getState = GetUninstallProtectionStateUseCase(repository, clock)

    // Re-derives from the persisted record instead of trusting UI state, so process
    // death, concurrent edits, or stale polls cannot spend a window that is not live.
    // Matching is on stable identity fields: remainingMs drifts with every refresh.
    suspend operator fun invoke(current: UninstallProtectionState): UninstallDecision {
        if (current !is UninstallProtectionState.Confirmation) return UninstallDecision.Cancelled
        val fresh = getState()
        val isLiveWindow = fresh is UninstallProtectionState.Confirmation &&
            fresh.availableAt == current.availableAt &&
            fresh.decisionDeadline == current.decisionDeadline
        return if (isLiveWindow) {
            repository.clear()
            UninstallDecision.Confirmed
        } else {
            UninstallDecision.Cancelled
        }
    }
}
