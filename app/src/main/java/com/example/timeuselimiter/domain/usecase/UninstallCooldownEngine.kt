package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.data.local.db.entity.UninstallCooldownEntity
import com.example.timeuselimiter.domain.model.UNINSTALL_COOLDOWN_MS
import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.time.TimeSource

internal class UninstallCooldownEngine(private val clock: TimeSource) {
    fun state(record: UninstallCooldownEntity?): UninstallProtectionState {
        if (record == null || !isValid(record)) return UninstallProtectionState.None
        val nowWall = clock.wallClockMs()
        val sameBoot = record.bootId == clock.bootId()
        // A wall-clock rollback during this boot is harmless because elapsedRealtime is authoritative.
        // After reboot, elapsedRealtime is reset, so a rollback is detected and fails closed.
        if (!sameBoot && nowWall < record.lastObservedWallMs) return UninstallProtectionState.None

        val remaining = if (sameBoot) {
            positiveDifference(record.deadlineElapsedMs, clock.elapsedRealtimeMs())
        } else {
            positiveDifference(record.deadlineWallMs, nowWall)
        }
        return if (remaining > 0) {
            UninstallProtectionState.Waiting(record.startedAtWallMs, record.deadlineWallMs, remaining)
        } else {
            UninstallProtectionState.Confirmation(record.deadlineWallMs)
        }
    }

    fun newRecord(): UninstallCooldownEntity? {
        val wall = clock.wallClockMs()
        val elapsed = clock.elapsedRealtimeMs()
        val wallDeadline = safeAdd(wall, UNINSTALL_COOLDOWN_MS) ?: return null
        val elapsedDeadline = safeAdd(elapsed, UNINSTALL_COOLDOWN_MS) ?: return null
        return UninstallCooldownEntity(
            startedAtWallMs = wall,
            deadlineWallMs = wallDeadline,
            startedAtElapsedMs = elapsed,
            deadlineElapsedMs = elapsedDeadline,
            bootId = clock.bootId(),
            lastObservedWallMs = wall
        )
    }

    private fun isValid(value: UninstallCooldownEntity): Boolean {
        val wallSpan = boundedSpan(value.deadlineWallMs, value.startedAtWallMs)
        val elapsedSpan = boundedSpan(value.deadlineElapsedMs, value.startedAtElapsedMs)
        return wallSpan != null && elapsedSpan != null &&
            value.lastObservedWallMs >= value.startedAtWallMs
    }

    private fun safeAdd(value: Long, increment: Long): Long? =
        if (value > Long.MAX_VALUE - increment) null else value + increment

    private fun positiveDifference(later: Long, earlier: Long): Long {
        if (later <= earlier) return 0
        val difference = later - earlier
        return if (difference < 0) UNINSTALL_COOLDOWN_MS else
            difference.coerceAtMost(UNINSTALL_COOLDOWN_MS)
    }

    private fun boundedSpan(later: Long, earlier: Long): Long? {
        if (later <= earlier) return null
        val difference = later - earlier
        return if (difference in 1..UNINSTALL_COOLDOWN_MS) difference else null
    }
}
