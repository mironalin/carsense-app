package com.carsense.features.bluetooth.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.features.bluetooth.domain.BluetoothController
import com.carsense.features.bluetooth.domain.BluetoothDeviceDomain
import com.carsense.features.bluetooth.domain.ConnectionResult
import com.carsense.features.bluetooth.presentation.intent.BluetoothIntent
import com.carsense.features.bluetooth.presentation.model.BluetoothState
import com.carsense.features.obd2.domain.OBD2MessageMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BluetoothViewModel @Inject constructor(private val bluetoothController: BluetoothController) :
    ViewModel() {

    private val _state = MutableStateFlow(BluetoothState())
    val state =
        combine(
            bluetoothController.scannedDevices,
            bluetoothController.pairedDevices,
            _state
        ) { scannedDevices, pairedDevices, state ->
            state.copy(
                scannedDevices = scannedDevices,
                pairedDevices = pairedDevices,
                messages = if (state.isConnected) state.messages else emptyList()
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    private var deviceConnectionJob: Job? = null

    init {
        bluetoothController
            .isConnected
            .onEach { isConnected -> _state.update { it.copy(isConnected = isConnected) } }
            .launchIn(viewModelScope)

        bluetoothController
            .errors
            .onEach { error -> _state.update { it.copy(errorMessage = error) } }
            .launchIn(viewModelScope)
    }

    /** Process user intents and update state accordingly */
    fun processIntent(intent: BluetoothIntent) {
        // First update state through the reducer
        _state.update { currentState -> reduce(currentState, intent) }

        // Then handle side effects
        when (intent) {
            is BluetoothIntent.ConnectToDevice -> connectToDevice(intent.device)
            is BluetoothIntent.DisconnectFromDevice -> disconnectFromDevice()
            is BluetoothIntent.StartScan -> startScan()
            is BluetoothIntent.StopScan -> stopScan()
            is BluetoothIntent.SendCommand -> sendMessage(intent.message)
            is BluetoothIntent.DismissError -> _state.update { it.copy(errorMessage = null) }
        }
    }

    /** Pure reducer function that transforms the current state based on the intent */
    private fun reduce(currentState: BluetoothState, intent: BluetoothIntent): BluetoothState {
        return when (intent) {
            is BluetoothIntent.ConnectToDevice -> currentState.copy(isConnecting = true)
            is BluetoothIntent.DisconnectFromDevice ->
                currentState.copy(isConnecting = false, isConnected = false)

            is BluetoothIntent.StartScan -> currentState
            is BluetoothIntent.StopScan -> currentState
            is BluetoothIntent.SendCommand -> currentState
            is BluetoothIntent.DismissError -> currentState.copy(errorMessage = null)
        }
    }

    private fun connectToDevice(device: BluetoothDeviceDomain) {
        deviceConnectionJob = bluetoothController.connectToDevice(device).listen()
    }

    private fun disconnectFromDevice() {
        deviceConnectionJob?.cancel()
        bluetoothController.closeConnection()
    }

    private fun sendMessage(message: String) {
        viewModelScope.launch {
            // Use only the OBD2 message API
            val obd2Message = bluetoothController.sendOBD2Command(message)
            if (obd2Message != null) {
                val bluetoothMessage = OBD2MessageMapper.convertToBluetoothMessage(obd2Message)
                _state.update { it.copy(messages = it.messages + bluetoothMessage) }
            }
        }
    }

    private fun startScan() {
        bluetoothController.startDiscovery()
    }

    private fun stopScan() {
        bluetoothController.stopDiscovery()
    }

    private fun Flow<ConnectionResult>.listen(): Job {
        return onEach { result ->
            when (result) {
                ConnectionResult.ConnectionEstablished -> {
                    // Connection established, but OBD2 initialization will happen in the
                    // controller
                    _state.update { it.copy(isConnecting = true, errorMessage = null) }
                }

                is ConnectionResult.TransferSucceeded -> {
                    // Receiving messages means connection and initialization succeeded
                    _state.update {
                        it.copy(
                            isConnected = true,
                            isConnecting = false,
                            messages =
                                it.messages +
                                        OBD2MessageMapper.convertToBluetoothMessage(
                                            result.message
                                        )
                        )
                    }
                }

                is ConnectionResult.Error -> {
                    _state.update {
                        it.copy(
                            isConnected = false,
                            isConnecting = false,
                            errorMessage = result.message
                        )
                    }
                    bluetoothController.closeConnection()
                }
            }
        }
            .catch { throwable ->
                bluetoothController.closeConnection()
                _state.update {
                    it.copy(
                        isConnected = false,
                        isConnecting = false,
                        errorMessage = throwable.message
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothController.release()
    }
}
