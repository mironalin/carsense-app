package com.carsense.features.dtc.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.features.diagnostics.data.DiagnosticSessionManager
import com.carsense.features.diagnostics.domain.repository.DiagnosticRepository
import com.carsense.features.dtc.domain.model.DTCError
import com.carsense.features.dtc.domain.repository.DTCRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DTCViewModel @Inject constructor(
    private val dtcRepository: DTCRepository,
    private val diagnosticRepository: DiagnosticRepository,
    private val diagnosticSessionManager: DiagnosticSessionManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val TAG = "DTCViewModel"

    // UI state for the DTC screen
    private val _state = MutableStateFlow(DTCState())
    val state: StateFlow<DTCState> = _state.asStateFlow()

    // Debug mode - set to true to see detailed information in error messages
    private val debugMode = true

    // Store the latest diagnostic UUID for use when sending DTCs
    private var latestDiagnosticUUID: String? = null

    init {
        loadCachedDTCs()
        fetchLatestDiagnosticUUID()
    }

    /**
     * Fetch the latest diagnostic UUID from the repository to use as a fallback
     */
    private fun fetchLatestDiagnosticUUID() {
        viewModelScope.launch {
            try {
                // First try to get from the session manager (highest priority)
                val sessionUUID = diagnosticSessionManager.getCurrentDiagnosticUUID()

                if (!sessionUUID.isNullOrBlank()) {
                    latestDiagnosticUUID = sessionUUID
                    Log.d(
                        TAG,
                        "Fetched diagnostic UUID from session manager: $latestDiagnosticUUID"
                    )
                    return@launch
                }

                // If not in session manager, fall back to repository
                val result = diagnosticRepository.getAllDiagnostics()
                result.onSuccess { diagnostics ->
                    if (diagnostics.isNotEmpty()) {
                        // Get the most recent diagnostic (assuming diagnostics are sorted by creation time)
                        latestDiagnosticUUID = diagnostics.firstOrNull()?.uuid
                        Log.d(
                            TAG,
                            "Fetched latest diagnostic UUID from repository: $latestDiagnosticUUID"
                        )
                    } else {
                        Log.w(TAG, "No diagnostics found in repository")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching latest diagnostic UUID", e)
            }
        }
    }

    // Load cached DTCs when the viewmodel is created
    private fun loadCachedDTCs() {
        val cachedDTCs = dtcRepository.getCachedDTCs()
        if (cachedDTCs.isNotEmpty()) {
            Log.d(TAG, "Loading ${cachedDTCs.size} cached DTCs")
            _state.value =
                _state.value.copy(dtcErrors = cachedDTCs, isLoading = false, error = null)
        }
    }

    // Load DTCs when the screen is opened
    fun loadDTCErrors() {
        viewModelScope.launch {
            Log.d(TAG, "Loading DTC errors")
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                // Ensure we have the latest diagnostic UUID before scanning
                fetchLatestDiagnosticUUID()

                // Get DTCs from the repository
                Log.d(TAG, "Calling repository.getDTCs()")
                val result = dtcRepository.getDTCs()

                result.fold(
                    onSuccess = { dtcErrors ->
                        Log.d(TAG, "DTCs loaded successfully: ${dtcErrors.size} errors")
                        _state.value =
                            _state.value.copy(
                                dtcErrors = dtcErrors,
                                isLoading = false,
                                error =
                                    if (dtcErrors.isEmpty()) {
                                        if (debugMode) {
                                            "NO_DTCS:No DTC errors found. Make sure your simulator has DTCs set and try again."
                                        } else {
                                            "NO_DTCS:No DTC errors found"
                                        }
                                    } else null
                            )

                        // If DTCs are found, silently send them to the backend
                        if (dtcErrors.isNotEmpty()) {
                            sendDTCsToBackendSilently()
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to load DTCs", error)
                        _state.value =
                            _state.value.copy(
                                isLoading = false,
                                error =
                                    if (debugMode) {
                                        "Failed to read DTCs: ${error.message}\nCheck logcat for more details."
                                    } else {
                                        "Failed to read DTCs: ${error.message}"
                                    }
                            )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading DTCs", e)
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error =
                            if (debugMode) {
                                "Error: ${e.message}\nCheck logcat for more details."
                            } else {
                                "Error: ${e.message}"
                            }
                    )
            }
        }
    }

    // Clear DTCs from the vehicle
    fun clearDTCErrors() {
        viewModelScope.launch {
            Log.d(TAG, "Clearing DTC errors")
            _state.value = _state.value.copy(isLoading = true, error = null)

            try {
                // Request to clear DTCs
                Log.d(TAG, "Calling repository.clearDTCs()")
                val result = dtcRepository.clearDTCs()

                result.fold(
                    onSuccess = { success ->
                        if (success) {
                            // If successful, clear the list in the UI
                            Log.d(TAG, "DTCs cleared successfully")
                            _state.value =
                                _state.value.copy(
                                    dtcErrors = emptyList(),
                                    isLoading = false,
                                    error = "DTCs cleared successfully"
                                )
                        } else {
                            Log.e(TAG, "Failed to clear DTCs")
                            _state.value =
                                _state.value.copy(
                                    isLoading = false,
                                    error = "Failed to clear DTCs"
                                )
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error clearing DTCs", error)
                        _state.value =
                            _state.value.copy(
                                isLoading = false,
                                error =
                                    if (debugMode) {
                                        "Error clearing DTCs: ${error.message}\nCheck logcat for more details."
                                    } else {
                                        "Error clearing DTCs: ${error.message}"
                                    }
                            )
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception clearing DTCs", e)
                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error =
                            if (debugMode) {
                                "Error: ${e.message}\nCheck logcat for more details."
                            } else {
                                "Error: ${e.message}"
                            }
                    )
            }
        }
    }

    /**
     * Silently sends DTCs to the backend without updating the UI state.
     * This is called after a successful manual DTC scan.
     * The operation is non-blocking and errors are only logged, not shown to the user.
     */
    private fun sendDTCsToBackendSilently() {
        // First try to get from saved state handle, then fall back to the stored value
        val diagnosticUUID = savedStateHandle.get<String>("diagnosticUUID") ?: latestDiagnosticUUID

        if (diagnosticUUID.isNullOrBlank()) {
            Log.w(
                TAG,
                "Cannot send DTCs: No diagnostic UUID available (neither in SavedStateHandle nor from session manager)"
            )
            return
        }

        // Launch in a separate coroutine to make it non-blocking
        viewModelScope.launch {
            Log.d(
                TAG,
                "Silently sending ${_state.value.dtcErrors.size} DTCs to backend for diagnostic $diagnosticUUID"
            )

            try {
                val result = dtcRepository.sendDTCsToBackend(diagnosticUUID)

                result.fold(
                    onSuccess = { count ->
                        Log.d(TAG, "Successfully sent $count DTCs to backend")
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Failed to send DTCs to backend", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Exception sending DTCs to backend", e)
            }
            // No UI state changes regardless of success or failure
        }
    }
}

// State class for the DTC screen
data class DTCState(
    val dtcErrors: List<DTCError> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
