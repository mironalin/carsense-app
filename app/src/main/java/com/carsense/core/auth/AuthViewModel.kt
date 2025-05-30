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
    val token: String? = null,
    val userName: String? = null,
    val userEmail: String? = null,
    val isLoading: Boolean = false // To indicate ongoing auth operations
)

sealed class AuthUIEvent {
    object LaunchLoginFlow : AuthUIEvent()
    data class ShowToast(val message: String) : AuthUIEvent()
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
            _state.update { it.copy(isLoading = true) }
            val token = tokenStorageService.getAuthToken()
            val loggedIn = token != null
            _state.update { it.copy(isLoggedIn = loggedIn, token = token, isLoading = false) }
            Timber.d("AuthViewModel: checkLoginState - isLoggedIn: $loggedIn")

            // If logged in, fetch user details
            if (loggedIn) {
                fetchSessionDetails()
            }
        }
    }

    /**
     * Fetches session details including user information from the server
     */
    fun fetchSessionDetails() {
        viewModelScope.launch {
            try {
                val sessionResponse = authManager.fetchSessionDetails()
                if (sessionResponse != null) {
                    _state.update {
                        it.copy(
                            userName = sessionResponse.user.name,
                            userEmail = sessionResponse.user.email
                        )
                    }
                    Timber.d("AuthViewModel: Updated user details - name: ${sessionResponse.user.name}, email: ${sessionResponse.user.email}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error fetching session details")
                // Don't update UI state on error, keep existing values
            }
        }
    }

    fun handleLoginLogoutClick() {
        viewModelScope.launch {
            if (_state.value.isLoggedIn) {
                _state.update { it.copy(isLoading = true) }
                val serverLogoutSuccess = authManager.logout() // Calls the new suspend function
                if (!serverLogoutSuccess) {
                    _uiEvents.emit(AuthUIEvent.ShowToast("Logout from server failed. Logged out locally."))
                }
                // AuthManager.logout() already clears local tokens via logoutFromDeviceOnly()
                _state.update {
                    it.copy(
                        isLoggedIn = false,
                        token = null,
                        userName = null,
                        userEmail = null,
                        isLoading = false
                    )
                }
                Timber.d("AuthViewModel: Logout processed. Server success: $serverLogoutSuccess")
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
                _state.update { it.copy(isLoading = true) }
                val serverLogoutSuccess = authManager.logout()
                if (!serverLogoutSuccess) {
                    _uiEvents.emit(AuthUIEvent.ShowToast("Logout from server failed. Logged out locally."))
                }
                _state.update {
                    it.copy(
                        isLoggedIn = false,
                        token = null,
                        userName = null,
                        userEmail = null,
                        isLoading = false
                    )
                }
                Timber.d("AuthViewModel: Direct logout processed. Server success: $serverLogoutSuccess")
            }
        }
    }

    /**
     * Refreshes user details by fetching the latest session data.
     * This can be called manually when needed, such as when resuming the app.
     */
    fun refreshUserDetails() {
        Timber.d("Manual refresh of user details requested")
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            fetchSessionDetails()
            _state.update { it.copy(isLoading = false) }
        }
    }
}