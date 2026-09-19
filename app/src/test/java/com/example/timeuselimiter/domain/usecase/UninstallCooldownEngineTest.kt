package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.data.local.db.entity.UninstallCooldownEntity
import com.example.timeuselimiter.domain.model.UNINSTALL_COOLDOWN_MS
import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.time.TimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTime(
    var wall: Long = 1_000_000L,
    var elapsed: Long = 5_000L,
    var boot: String = "a"
) : TimeSource {
    override fun wallClockMs() = wall
    override fun elapsedRealtimeMs() = elapsed
    override fun bootId() = boot
}

class UninstallCooldownEngineTest {
    @Test fun `normal cycle uses monotonic elapsed time`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time)
        val record = engine.newRecord()!!
        time.wall += UNINSTALL_COOLDOWN_MS + 5_000
        time.elapsed += UNINSTALL_COOLDOWN_MS + 5_000
        assertTrue(engine.state(record) is UninstallProtectionState.Confirmation)
    }

    @Test fun `backward wall clock cannot extend same boot cycle`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time); val record = engine.newRecord()!!
        time.wall -= UNINSTALL_COOLDOWN_MS * 2; time.elapsed += UNINSTALL_COOLDOWN_MS + 5_000
        assertTrue(engine.state(record) is UninstallProtectionState.Confirmation)
    }

    @Test fun `reboot uses wall deadline and rollback fails closed`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time); val record = engine.newRecord()!!
        time.boot = "b"; time.wall += 1; time.elapsed = 1
        assertTrue(engine.state(record) is UninstallProtectionState.Waiting)
        time.wall = record.startedAtWallMs - 1
        // Fail closed: a rolled-back clock keeps enforcing instead of discarding the record.
        val state = engine.state(record) as UninstallProtectionState.Waiting
        // Remaining is capped at one full cycle even though the raw difference is span + 1.
        assertEquals(UNINSTALL_COOLDOWN_MS, state.remainingMs)
    }

    @Test fun `decision window opens after cooldown and expires back to none`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time); val record = engine.newRecord()!!
        time.wall += UNINSTALL_COOLDOWN_MS + 5_000
        time.elapsed += UNINSTALL_COOLDOWN_MS + 5_000
        val open = engine.state(record)
        assertTrue(open is UninstallProtectionState.Confirmation)
        assertEquals(UNINSTALL_COOLDOWN_MS - 5_000, open.remainingMs)
        time.wall += UNINSTALL_COOLDOWN_MS + 1
        time.elapsed += UNINSTALL_COOLDOWN_MS + 1
        assertEquals(UninstallProtectionState.None, engine.state(record))
    }

    @Test fun `expired decision window on reboot uses wall clock`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time); val record = engine.newRecord()!!
        time.boot = "b"; time.elapsed = 1
        time.wall = record.decisionDeadlineWallMs + 1
        assertEquals(UninstallProtectionState.None, engine.state(record))
    }

    @Test fun `observation watermark advances and is persisted`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time); val record = engine.newRecord()!!
        assertNull(engine.advancedObservation) // nothing yet
        time.wall += 120_000
        engine.state(record)
        val advanced = engine.advancedObservation
        assertEquals(time.wall, advanced!!.lastObservedWallMs)
        assertEquals(record.startedAtWallMs, advanced.startedAtWallMs)
    }

    @Test fun `watermark is throttled under one minute`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time); val record = engine.newRecord()!!
        time.wall += 59_000
        engine.state(record)
        assertNull(engine.advancedObservation)
    }

    @Test fun `corrupt duration and max values cannot produce a wait`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time)
        // decision deadlines are 0, so the deadline->decision spans are invalid -> None
        val corrupt = UninstallCooldownEntity(
            1, 0, Long.MAX_VALUE, 0, Long.MAX_VALUE, 0, 0, "a", 0
        )
        assertEquals(UninstallProtectionState.None, engine.state(corrupt))

        val overflowing = corrupt.copy(startedAtWallMs = -1, startedAtElapsedMs = -1, lastObservedWallMs = 0)
        assertEquals(UninstallProtectionState.None, engine.state(overflowing))
    }

    @Test fun `overflow when starting produces no cycle`() {
        val time = FakeTime(wall = Long.MAX_VALUE - 1, elapsed = Long.MAX_VALUE - 1)
        assertNull(UninstallCooldownEngine(time).newRecord())
    }
}
