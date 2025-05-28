package com.carsense.core.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class AuthState(
    val isLoggedIn: Boolean = false,
    val token: String? = null
)

sealed class AuthUIEvent {
    object LaunchLoginFlow : AuthUIEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val tokenStorageService: TokenStorageService
) : ViewModel() {

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val _uiEvents = MutableSharedFlow<AuthUIEvent>()
    val uiEvents: SharedFlow<AuthUIEvent> = _uiEvents.asSharedFlow()

    init {
        checkLoginState()
    }

    fun checkLoginState() {
        viewModelScope.launch {
            val token = tokenStorageService.getAuthToken()
            val loggedIn = token != null
            _state.update { it.copy(isLoggedIn = loggedIn, token = token) }
            Timber.d("AuthViewModel: checkLoginState - isLoggedIn: $loggedIn")
        }
    }

    fun handleLoginLogoutClick() {
        viewModelScope.launch {
            if (_state.value.isLoggedIn) {
                authManager.logout()
                _state.update { it.copy(isLoggedIn = false, token = null) }
                Timber.d("AuthViewModel: Logout processed.")
            } else {
                // Not logged in, trigger UI event to launch login flow
                _uiEvents.emit(AuthUIEvent.LaunchLoginFlow)
                Timber.d("AuthViewModel: LaunchLoginFlow event emitted.")
            }
        }
    }

    // Keep this for when logout is called directly (e.g. from a dedicated logout button elsewhere)
    fun logout() {
        viewModelScope.launch {
            if (_state.value.isLoggedIn) { // Ensure we only logout if actually logged in
                authManager.logout()
                _state.update { it.copy(isLoggedIn = false, token = null) }
                Timber.d("AuthViewModel: Direct logout processed.")
            }
        }
    }
}