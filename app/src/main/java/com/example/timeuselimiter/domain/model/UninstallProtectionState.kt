package com.example.timeuselimiter.domain.model

const val UNINSTALL_COOLDOWN_MS = 24L * 60 * 60 * 1000

sealed interface UninstallProtectionState {
    data object None : UninstallProtectionState

    data class Waiting(
        val startedAt: Long,
        val deadline: Long,
        val remainingMs: Long
    ) : UninstallProtectionState

    data class Confirmation(val availableAt: Long) : UninstallProtectionState
}
