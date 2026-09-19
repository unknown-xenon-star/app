package com.example.timeuselimiter.presentation.uninstall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timeuselimiter.domain.model.UNINSTALL_REFLECTION_MS
import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository
import com.example.timeuselimiter.domain.time.TimeSource
import com.example.timeuselimiter.domain.usecase.ContinueUninstallUseCase
import com.example.timeuselimiter.domain.usecase.GetUninstallProtectionStateUseCase
import com.example.timeuselimiter.domain.usecase.KeepAppUseCase
import com.example.timeuselimiter.domain.usecase.RequestUninstallUseCase
import com.example.timeuselimiter.domain.usecase.RunUninstallReflectionUseCase
import com.example.timeuselimiter.domain.usecase.UninstallDecision
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UninstallViewModel(
    repository: UninstallProtectionRepository,
    clock: TimeSource
) : ViewModel() {
    private val getState = GetUninstallProtectionStateUseCase(repository, clock)
    private val request = RequestUninstallUseCase(repository, clock)
    private val keep = KeepAppUseCase(repository)
    private val continueUninstall = ContinueUninstallUseCase(repository, clock)
    private val reflect = RunUninstallReflectionUseCase()
    private val _state = MutableStateFlow<UninstallProtectionState>(UninstallProtectionState.None)
    val state: StateFlow<UninstallProtectionState> = _state.asStateFlow()

    // One-shot event: consumed by the Activity to launch the system uninstall dialog.
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    // Single poller: avoids coroutine pile-up and stops clobbering the transient
    // Reflecting state every second during the reflection delay.
    private var pollJob: Job? = null

    init {
        startPolling()
    }

    fun requestUninstall() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            _state.value = request()
            startPolling()
        }
    }

    fun keepApp() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            keep()
            _state.value = UninstallProtectionState.None
            startPolling()
        }
    }

    fun continueUninstall() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val current = _state.value
            when (continueUninstall(current)) {
                UninstallDecision.Confirmed -> {
                    _state.value = UninstallProtectionState.Reflecting(UNINSTALL_REFLECTION_MS)
                    // Record is already cleared, so a process death during reflection
                    // simply resets protection and requires a fresh cooldown.
                    reflect { remaining ->
                        _state.value = UninstallProtectionState.Reflecting(remaining)
                    }
                    _events.tryEmit(Unit)
                }
                UninstallDecision.Cancelled -> {
                    _state.value = getState()
                    startPolling()
                }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                _state.value = getState()
                delay(1_000)
            }
        }
    }
}
