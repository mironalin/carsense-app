package com.carsense.features.obd2.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.features.obd2.domain.repository.DTCRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DTCError(val code: String, val description: String)

@HiltViewModel
class DTCViewModel @Inject constructor(private val dtcRepository: DTCRepository) : ViewModel() {
    private val TAG = "DTCViewModel"

    // UI state for the DTC screen
    private val _state = MutableStateFlow(DTCState())
    val state: StateFlow<DTCState> = _state.asStateFlow()

    // Debug mode - set to true to see detailed information in error messages
    private val debugMode = true

    init {
        loadCachedDTCs()
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
}

// State class for the DTC screen
data class DTCState(
    val dtcErrors: List<DTCError> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
