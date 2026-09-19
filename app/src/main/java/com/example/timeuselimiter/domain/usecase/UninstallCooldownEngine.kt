package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.data.local.db.entity.UninstallCooldownEntity
import com.example.timeuselimiter.domain.model.UNINSTALL_COOLDOWN_MS
import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.time.TimeSource

internal class UninstallCooldownEngine(private val clock: TimeSource) {

    // Non-null when the latest observation moved the watermark forward enough to persist.
    // Callers must save it so cross-boot rollback detection cannot be replayed.
    var advancedObservation: UninstallCooldownEntity? = null
        private set

    fun state(record: UninstallCooldownEntity?): UninstallProtectionState {
        advancedObservation = null
        if (record == null || !isValid(record)) return UninstallProtectionState.None

        val nowWall = clock.wallClockMs()
        val nowElapsed = clock.elapsedRealtimeMs()
        val sameBoot = record.bootId == clock.bootId()

        // In-boot wall rollbacks are harmless: elapsedRealtime is authoritative.
        // Across a reboot elapsedRealtime resets, so the wall clock is authoritative:
        // if it was rolled back, fail closed and keep enforcing the wall deadline.
        if (!sameBoot && nowWall < record.lastObservedWallMs) {
            // Cap at one cycle: a rolled-back clock must never render an absurd
            // multi-year wait, and one full cooldown is the fail-closed maximum.
            return UninstallProtectionState.Waiting(
                record.startedAtWallMs,
                record.deadlineWallMs,
                positiveDifference(record.deadlineWallMs, nowWall).coerceAtLeast(1L)
            )
        }

        // Advance the rollback-detection watermark, throttled so polling does not
        // hammer the database; an undetected small rollback only ever extends the
        // remaining wait, which is the safe direction.
        if (nowWall - record.lastObservedWallMs >= OBSERVATION_GRANULARITY_MS) {
            advancedObservation = record.copy(lastObservedWallMs = nowWall)
        }

        val remaining = if (sameBoot) {
            positiveDifference(record.deadlineElapsedMs, nowElapsed)
        } else {
            positiveDifference(record.deadlineWallMs, nowWall)
        }
        if (remaining > 0) {
            return UninstallProtectionState.Waiting(record.startedAtWallMs, record.deadlineWallMs, remaining)
        }

        // Decision window: same authoritative clock choice as the wait phase. If the
        // user does not confirm before it lapses, the cycle is abandoned and a fresh
        // cooldown is required.
        val windowRemaining = if (sameBoot) {
            positiveDifference(record.decisionDeadlineElapsedMs, nowElapsed)
        } else {
            positiveDifference(record.decisionDeadlineWallMs, nowWall)
        }
        return if (windowRemaining > 0) {
            UninstallProtectionState.Confirmation(
                availableAt = record.deadlineWallMs,
                decisionDeadline = record.decisionDeadlineWallMs,
                remainingMs = windowRemaining
            )
        } else {
            UninstallProtectionState.None
        }
    }

    fun newRecord(): UninstallCooldownEntity? {
        val wall = clock.wallClockMs()
        val elapsed = clock.elapsedRealtimeMs()
        val wallDeadline = safeAdd(wall, UNINSTALL_COOLDOWN_MS) ?: return null
        val elapsedDeadline = safeAdd(elapsed, UNINSTALL_COOLDOWN_MS) ?: return null
        val wallDecision = safeAdd(wallDeadline, UNINSTALL_COOLDOWN_MS) ?: return null
        val elapsedDecision = safeAdd(elapsedDeadline, UNINSTALL_COOLDOWN_MS) ?: return null
        return UninstallCooldownEntity(
            startedAtWallMs = wall,
            deadlineWallMs = wallDeadline,
            startedAtElapsedMs = elapsed,
            deadlineElapsedMs = elapsedDeadline,
            decisionDeadlineWallMs = wallDecision,
            decisionDeadlineElapsedMs = elapsedDecision,
            bootId = clock.bootId(),
            lastObservedWallMs = wall
        )
    }

    private fun isValid(value: UninstallCooldownEntity): Boolean {
        val wallSpan = boundedSpan(value.deadlineWallMs, value.startedAtWallMs)
        val elapsedSpan = boundedSpan(value.deadlineElapsedMs, value.startedAtElapsedMs)
        val wallWindow = boundedSpan(value.decisionDeadlineWallMs, value.deadlineWallMs)
        val elapsedWindow = boundedSpan(value.decisionDeadlineElapsedMs, value.deadlineElapsedMs)
        return wallSpan != null && elapsedSpan != null &&
            wallWindow != null && elapsedWindow != null &&
            value.lastObservedWallMs >= value.startedAtWallMs
    }

    private fun safeAdd(value: Long, increment: Long): Long? =
        if (value > Long.MAX_VALUE - increment) null else value + increment

    private fun positiveDifference(later: Long, earlier: Long): Long {
        if (later <= earlier) return 0
        return (later - earlier).coerceAtMost(UNINSTALL_COOLDOWN_MS)
    }

    private fun boundedSpan(later: Long, earlier: Long): Long? {
        if (later <= earlier) return null
        val difference = later - earlier
        return if (difference in 1..UNINSTALL_COOLDOWN_MS) difference else null
    }

    private companion object {
        const val OBSERVATION_GRANULARITY_MS = 60_000L
    }
}
