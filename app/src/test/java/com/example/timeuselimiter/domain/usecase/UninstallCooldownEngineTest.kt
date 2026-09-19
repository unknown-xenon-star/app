package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.data.local.db.entity.UninstallCooldownEntity
import com.example.timeuselimiter.domain.model.UNINSTALL_COOLDOWN_MS
import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.time.TimeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTime(var wall: Long = 1_000_000L, var elapsed: Long = 5_000L, var boot: String = "a") : TimeSource {
    override fun wallClockMs() = wall
    override fun elapsedRealtimeMs() = elapsed
    override fun bootId() = boot
}

class UninstallCooldownEngineTest {
    @Test fun `normal cycle uses monotonic elapsed time`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time)
        val record = engine.newRecord()!!
        time.wall += UNINSTALL_COOLDOWN_MS
        assertTrue(engine.state(record) is UninstallProtectionState.Confirmation)
    }

    @Test fun `backward wall clock cannot extend same boot cycle`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time); val record = engine.newRecord()!!
        time.wall -= UNINSTALL_COOLDOWN_MS * 2; time.elapsed += UNINSTALL_COOLDOWN_MS
        assertTrue(engine.state(record) is UninstallProtectionState.Confirmation)
    }

    @Test fun `reboot uses wall deadline and backward clock fails closed`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time); val record = engine.newRecord()!!
        time.boot = "b"; time.wall += 1; time.elapsed = 1
        assertTrue(engine.state(record) is UninstallProtectionState.Waiting)
        time.wall = record.startedAtWallMs - 1
        assertEquals(UninstallProtectionState.None, engine.state(record))
    }

    @Test fun `corrupt duration and max values cannot produce a wait`() {
        val time = FakeTime(); val engine = UninstallCooldownEngine(time)
        val corrupt = UninstallCooldownEntity(1, 0, Long.MAX_VALUE, 0, Long.MAX_VALUE, "a", 0)
        assertEquals(UninstallProtectionState.None, engine.state(corrupt))

        val overflowing = corrupt.copy(startedAtWallMs = -1, startedAtElapsedMs = -1, lastObservedWallMs = 0)
        assertEquals(UninstallProtectionState.None, engine.state(overflowing))
    }

    @Test fun `overflow when starting produces no cycle`() {
        val time = FakeTime(wall = Long.MAX_VALUE - 1, elapsed = Long.MAX_VALUE - 1)
        assertEquals(null, UninstallCooldownEngine(time).newRecord())
    }
}
