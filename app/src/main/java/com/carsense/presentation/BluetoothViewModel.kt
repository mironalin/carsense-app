package com.carsense.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.domain.bluetooth.BluetoothController
import com.carsense.domain.bluetooth.BluetoothDeviceDomain
import com.carsense.domain.bluetooth.ConnectionResult
import com.carsense.domain.obd2.OBD2MessageMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class BluetoothViewModel @Inject constructor(private val bluetoothController: BluetoothController) :
    ViewModel() {

    private val _state = MutableStateFlow(BluetoothUiState())
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

    fun connectToDevice(device: BluetoothDeviceDomain) {
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController.connectToDevice(device).listen()
    }

    fun disconnectFromDevice() {
        deviceConnectionJob?.cancel()
        bluetoothController.closeConnection()
        _state.update { it.copy(isConnecting = false, isConnected = false) }
    }

    fun waitForIncomingConnections() {
        _state.update { it.copy(isConnecting = true) }
        deviceConnectionJob = bluetoothController.startBluetoothServer().listen()
    }

    fun sendMessage(message: String) {
        viewModelScope.launch {
            // Use only the OBD2 message API
            val obd2Message = bluetoothController.sendOBD2Command(message)
            if (obd2Message != null) {
                val bluetoothMessage = OBD2MessageMapper.convertToBluetoothMessage(obd2Message)
                _state.update { it.copy(messages = it.messages + bluetoothMessage) }
            }
        }
    }

    fun startScan() {
        bluetoothController.startDiscovery()
    }

    fun stopScan() {
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
