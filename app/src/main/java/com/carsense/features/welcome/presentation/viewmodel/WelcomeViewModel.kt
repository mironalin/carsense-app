package com.carsense.features.welcome.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.features.bluetooth.domain.BluetoothController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val bluetoothController: BluetoothController
) : ViewModel() {

    private val _state = MutableStateFlow(WelcomeState())
    val state: StateFlow<WelcomeState> = _state.asStateFlow()

    private var connectionTimeJob: Job? = null

    init {
        // Monitor connection state to track connection time and fetch adapter details
        viewModelScope.launch {
            bluetoothController.isConnected.collectLatest { isConnected ->
                if (isConnected) {
                    // Start connection timer
                    startConnectionTimeTracking()
                    // Fetch adapter details
                    fetchAdapterDetails()
                } else {
                    // Stop connection timer
                    connectionTimeJob?.cancel()
                    // Reset adapter details and connection time
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            connectionStartTime = 0L,
                            connectionTimeSeconds = 0,
                            adapterProtocol = null,
                            adapterFirmware = null
                        )
                    }
                }
            }
        }
    }

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

            is WelcomeEvent.RefreshAdapterDetails -> {
                fetchAdapterDetails()
            }
        }
    }

    private fun startConnectionTimeTracking() {
        // Cancel any existing job
        connectionTimeJob?.cancel()

        // Record connection start time
        val startTimeMillis = System.currentTimeMillis()
        _state.update { it.copy(connectionStartTime = startTimeMillis) }

        // Start a new tracking job
        connectionTimeJob = viewModelScope.launch {
            while (true) {
                val currentTimeMillis = System.currentTimeMillis()
                val elapsedSeconds =
                    TimeUnit.MILLISECONDS.toSeconds(currentTimeMillis - startTimeMillis)
                _state.update { it.copy(connectionTimeSeconds = elapsedSeconds) }
                delay(1000) // Update every second
            }
        }
    }

    private fun fetchAdapterDetails() {
        viewModelScope.launch {
            val obd2Service = bluetoothController.getObd2Service() ?: return@launch

            // Fetch adapter firmware version (ATI command)
            try {
                val firmwareResponse = obd2Service.executeAtCommand("ATI").firstOrNull()
                if (firmwareResponse != null && !firmwareResponse.isError) {
                    _state.update { it.copy(adapterFirmware = firmwareResponse.rawData) }
                }
            } catch (e: Exception) {
                // Log error but don't update UI with error message
            }

            // Fetch current protocol (ATDP command)
            try {
                val protocolResponse = obd2Service.executeAtCommand("ATDP").firstOrNull()
                if (protocolResponse != null && !protocolResponse.isError) {
                    _state.update { it.copy(adapterProtocol = protocolResponse.rawData) }
                }
            } catch (e: Exception) {
                // Log error but don't update UI with error message
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectionTimeJob?.cancel()
    }
}

data class WelcomeState(
    val isDarkMode: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionStartTime: Long = 0L,
    val connectionTimeSeconds: Long = 0L,
    val adapterProtocol: String? = null,
    val adapterFirmware: String? = null
)

sealed class WelcomeEvent {
    object ToggleDarkMode : WelcomeEvent()
    object OpenSettings : WelcomeEvent()
    object Connect : WelcomeEvent()
    object RefreshAdapterDetails : WelcomeEvent()
}
