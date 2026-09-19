package com.example.timeuselimiter.presentation.uninstall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.timeuselimiter.domain.model.UninstallProtectionState
import com.example.timeuselimiter.domain.repository.UninstallProtectionRepository
import com.example.timeuselimiter.domain.time.TimeSource
import com.example.timeuselimiter.domain.usecase.ContinueUninstallUseCase
import com.example.timeuselimiter.domain.usecase.GetUninstallProtectionStateUseCase
import com.example.timeuselimiter.domain.usecase.KeepAppUseCase
import com.example.timeuselimiter.domain.usecase.RequestUninstallUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val _state = MutableStateFlow<UninstallProtectionState>(UninstallProtectionState.None)
    val state: StateFlow<UninstallProtectionState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                refresh()
            }
        }
    }

    fun requestUninstall() = viewModelScope.launch { _state.value = request() }

    fun keepApp() = viewModelScope.launch {
        keep()
        _state.value = UninstallProtectionState.None
    }

    fun continueUninstall() = viewModelScope.launch { _state.value = continueUninstall(_state.value) }

    private fun refresh() = viewModelScope.launch { _state.value = getState() }
}
