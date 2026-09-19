package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.data.local.db.entity.UninstallCooldownEntity
import com.example.timeuselimiter.domain.model.UNINSTALL_COOLDOWN_MS
import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository
import com.example.timeuselimiter.domain.time.TimeSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class UseCaseTime(var now: Long = 1_000_000L, var elapsed: Long = 5_000L) : TimeSource {
    override fun wallClockMs() = now
    override fun elapsedRealtimeMs() = elapsed
    override fun bootId() = "boot"
}

private class MemoryCooldownRepository : UninstallProtectionRepository {
    var value: UninstallCooldownEntity? = null
    override suspend fun get() = value
    override suspend fun save(value: UninstallCooldownEntity) { this.value = value }
    override suspend fun clear() { value = null }
}

class UninstallCooldownUseCaseTest {
    @Test fun `keep app clears the persisted request`() = runTest {
        val repository = MemoryCooldownRepository(); val time = UseCaseTime()
        RequestUninstallUseCase(repository, time)()
        KeepAppUseCase(repository)()
        assertNull(repository.value)
    }

    @Test fun `confirmation window lasts exactly 24 hours`() = runTest {
        val repository = MemoryCooldownRepository(); val time = UseCaseTime()
        RequestUninstallUseCase(repository, time)()
        val record = repository.value!!
        assertEquals(UNINSTALL_COOLDOWN_MS, record.decisionDeadlineWallMs - record.deadlineWallMs)
        assertEquals(UNINSTALL_COOLDOWN_MS, record.decisionDeadlineElapsedMs - record.deadlineElapsedMs)
    }

    @Test fun `continue within window confirms and clears record`() = runTest {
        val repository = MemoryCooldownRepository(); val time = UseCaseTime()
        RequestUninstallUseCase(repository, time)()
        time.now += UNINSTALL_COOLDOWN_MS + 1_000
        time.elapsed += UNINSTALL_COOLDOWN_MS + 1_000
        val state = GetUninstallProtectionStateUseCase(repository, time)()
        assertTrue(state is UninstallProtectionState.Confirmation)
        val result = ContinueUninstallUseCase(repository, time)(state)
        assertEquals(UninstallDecision.Confirmed, result)
        assertNull(repository.value)
    }

    @Test fun `lapsed window resets and a fresh cooldown is required`() = runTest {
        val repository = MemoryCooldownRepository(); val time = UseCaseTime()
        RequestUninstallUseCase(repository, time)()
        val oldDeadline = repository.value!!.decisionDeadlineWallMs
        time.now += 2 * UNINSTALL_COOLDOWN_MS + 1_000
        time.elapsed += 2 * UNINSTALL_COOLDOWN_MS + 1_000
        assertEquals(UninstallProtectionState.None, GetUninstallProtectionStateUseCase(repository, time)())
        assertNull(repository.value)
        // Starting again produces a brand-new cycle anchored at the new time.
        RequestUninstallUseCase(repository, time)()
        assertEquals(time.now + UNINSTALL_COOLDOWN_MS, repository.value!!.deadlineWallMs)
        assertTrue(oldDeadline < repository.value!!.deadlineWallMs)
    }

    @Test fun `continue is refused when window already lapsed`() = runTest {
        val repository = MemoryCooldownRepository(); val time = UseCaseTime()
        RequestUninstallUseCase(repository, time)()
        val stale = UninstallProtectionState.Confirmation(
            availableAt = time.now + UNINSTALL_COOLDOWN_MS,
            decisionDeadline = time.now + 2 * UNINSTALL_COOLDOWN_MS,
            remainingMs = 1_000
        )
        time.now += 3 * UNINSTALL_COOLDOWN_MS
        time.elapsed += 3 * UNINSTALL_COOLDOWN_MS
        val result = ContinueUninstallUseCase(repository, time)(stale)
        assertEquals(UninstallDecision.Cancelled, result)
        assertNull(repository.value)
    }

    @Test fun `continue is refused while still waiting`() = runTest {
        val repository = MemoryCooldownRepository(); val time = UseCaseTime()
        RequestUninstallUseCase(repository, time)()
        val waiting = GetUninstallProtectionStateUseCase(repository, time)()
        val result = ContinueUninstallUseCase(repository, time)(waiting)
        assertEquals(UninstallDecision.Cancelled, result)
        assertTrue(repository.value != null)
    }

    @Test fun `a new use case instance recovers after process death`() = runTest {
        val repository = MemoryCooldownRepository(); val time = UseCaseTime()
        RequestUninstallUseCase(repository, time)()
        assertTrue(GetUninstallProtectionStateUseCase(repository, time)() is UninstallProtectionState.Waiting)
    }

    @Test fun `same boot rollback cannot shorten the wait`() = runTest {
        val repository = MemoryCooldownRepository(); val time = UseCaseTime()
        RequestUninstallUseCase(repository, time)()
        time.now -= UNINSTALL_COOLDOWN_MS
        assertTrue(GetUninstallProtectionStateUseCase(repository, time)() is UninstallProtectionState.Waiting)
    }
}
