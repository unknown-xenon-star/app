package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.domain.model.UNINSTALL_REFLECTION_MS
import kotlinx.coroutines.delay

/**
 * Runs the mandatory reflection delay after a confirmation window has been spent.
 * Emits [onTick] with the remaining milliseconds every second so the UI can render
 * a countdown. Only the [UninstallDecision.Confirmed] decision reaches this point:
 * record lifecycle is already handled by [ContinueUninstallUseCase] and
 * [GetUninstallProtectionStateUseCase]. Not persisted — a process death during
 * reflection simply resets protection and requires a fresh cooldown.
 */
class RunUninstallReflectionUseCase {
    suspend operator fun invoke(onTick: (remainingMs: Long) -> Unit): UninstallDecision {
        var remaining = UNINSTALL_REFLECTION_MS
        while (remaining > 0) {
            onTick(remaining)
            delay(1_000)
            remaining -= 1_000
        }
        return UninstallDecision.Confirmed
    }
}
