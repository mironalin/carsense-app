package com.carsense.features.welcome.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(WelcomeState())
    val state: StateFlow<WelcomeState> = _state.asStateFlow()

    fun onEvent(event: WelcomeEvent) {
        when (event) {
            is WelcomeEvent.ToggleDarkMode -> {
                viewModelScope.launch {
                    _state.update { it.copy(isDarkMode = !it.isDarkMode) }
                    // In a real app, save the preference to DataStore
                }
            }

            is WelcomeEvent.OpenSettings -> {
                // Handle navigation to settings
            }

            is WelcomeEvent.Connect -> {
                // The actual connection is handled in MainActivity
                _state.update { it.copy(isConnecting = true) }
            }
        }
    }
}

data class WelcomeState(val isDarkMode: Boolean = false, val isConnecting: Boolean = false)

sealed class WelcomeEvent {
    object ToggleDarkMode : WelcomeEvent()
    object OpenSettings : WelcomeEvent()
    object Connect : WelcomeEvent()
}
