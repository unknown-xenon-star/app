package com.example.timeuselimiter.domain.model

const val UNINSTALL_COOLDOWN_MS = 24L * 60 * 60 * 1000

/** Mandatory reflection delay after the user confirms uninstall (README spec). */
const val UNINSTALL_REFLECTION_MS = 10_000L

sealed interface UninstallProtectionState {
    data object None : UninstallProtectionState

    data class Waiting(
        val startedAt: Long,
        val deadline: Long,
        val remainingMs: Long
    ) : UninstallProtectionState

    data class Confirmation(
        val availableAt: Long,
        val decisionDeadline: Long,
        val remainingMs: Long
    ) : UninstallProtectionState

    /**
     * Transient, in-memory only: the confirmation window has been spent and the
     * uninstall fires after the reflection delay. Never persisted — if the process
     * dies mid-reflection, protection resets and a fresh cooldown is required.
     */
    data class Reflecting(val remainingMs: Long) : UninstallProtectionState
}
