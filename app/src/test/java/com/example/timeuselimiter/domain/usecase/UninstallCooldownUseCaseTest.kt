package com.example.timeuselimiter.domain.usecase

import com.example.timeuselimiter.data.local.db.entity.UninstallCooldownEntity
import com.example.timeuselimiter.domain.model.UNINSTALL_COOLDOWN_MS
import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository
import com.example.timeuselimiter.domain.time.TimeSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        assertEquals(null, repository.value)
    }

    @Test fun `continue starts another fixed 24 hour cycle`() = runTest {
        val repository = MemoryCooldownRepository(); val time = UseCaseTime()
        val first = RequestUninstallUseCase(repository, time)()
        time.elapsed += UNINSTALL_COOLDOWN_MS; time.now += UNINSTALL_COOLDOWN_MS
        val available = GetUninstallProtectionStateUseCase(repository, time)()
        val second = ContinueUninstallUseCase(repository, time)(available)
        assertTrue(first is UninstallProtectionState.Waiting)
        assertTrue(second is UninstallProtectionState.Waiting)
        assertEquals(UNINSTALL_COOLDOWN_MS, repository.value!!.deadlineWallMs - repository.value!!.startedAtWallMs)
    }

    @Test fun `a new use case instance recovers after process death`() = runTest {
        val repository = MemoryCooldownRepository(); val time = UseCaseTime()
        RequestUninstallUseCase(repository, time)()
        assertTrue(GetUninstallProtectionStateUseCase(repository, time)() is UninstallProtectionState.Waiting)
    }
}
