package com.carsense.features.bluetooth.presentation.viewmodel

import android.util.Log
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing Bluetooth-related UI state and interactions.
 *
 * This ViewModel interfaces with the [BluetoothController] to:
 * - Expose [BluetoothState] to the UI, including lists of scanned and paired devices,
 *   connection status ([BluetoothState.isConnected], [BluetoothState.isConnecting]),
 *   error messages, and received OBD2 messages.
 * - Process user [BluetoothIntent]s such as starting/stopping scans, connecting/disconnecting
 *   from devices, and sending OBD2 commands.
 * - Observe connection status and errors from the [BluetoothController] and update its state accordingly.
 * - Manage the lifecycle of a device connection attempt via [deviceConnectionJob].
 *
 * It uses a combination of `combine` and `stateIn` to create an observable [state] Flow,
 * and a simple `reduce` pattern internally for some state transitions based on intents.
 *
 * @param bluetoothController The [BluetoothController] implementation responsible for handling
 *   the actual Bluetooth operations.
 */
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
        // Observe the connection status from the BluetoothController.
        // This flow reflects the underlying Bluetooth socket's ACL (Asynchronous Connection-Less)
        // state and, more importantly, whether the OBD2BluetoothService within the controller
        // has been successfully initialized. This is the key indicator of whether the connection
        // is established and ready for OBD2 communication.
        bluetoothController
            .isConnected // Observe the connection status from the BluetoothController.
            .onEach { controllerIsConnected ->
                val oldState = _state.value // For logging context
                _state.update { currentState ->
                    // When the controller reports a connection change:
                    // If controllerIsConnected is true:
                    //   - Mark ViewModel's isConnected as true.
                    //   - Mark isConnecting as false (as the full connection, including OBD2 init, is complete).
                    //   - Clear any previous error messages.
                    // If controllerIsConnected is false:
                    //   - Mark ViewModel's isConnected as false.
                    //   - If we were in a connecting state (currentState.isConnecting was true)
                    //     and had a target device (connectedDeviceAddress was not null),
                    //     this means the connection attempt failed or was externally dropped.
                    //     So, set isConnecting to false.
                    //   - Also, if isConnecting was true, clear connectedDeviceAddress as the attempt ended.
                    //   - Otherwise (if not actively connecting), retain current isConnecting state
                    //     (e.g. if it was already false).
                    val newState = if (controllerIsConnected) {
                        currentState.copy(
                            isConnected = true,
                            isConnecting = false, // The full connection, including OBD2 init, is complete.
                            errorMessage = null
                        )
                    } else {
                        // If we were in the middle of a connection attempt that now failed/dropped.
                        currentState.copy(
                            isConnected = false,
                            isConnecting = if (currentState.isConnecting && currentState.connectedDeviceAddress != null) false else currentState.isConnecting,
                            // Clear target device if connection attempt failed/dropped
                            connectedDeviceAddress = if (currentState.isConnecting) null else currentState.connectedDeviceAddress
                        )
                    }
                    Log.d(
                        "BluetoothViewModel",
                        "controller.isConnected changed to: $controllerIsConnected. OldVMState: (conn=${oldState.isConnected}, connecting=${oldState.isConnecting}, addr=${oldState.connectedDeviceAddress}). NewVMState: (conn=${newState.isConnected}, connecting=${newState.isConnecting}, addr=${newState.connectedDeviceAddress})"
                    )
                    newState
                }
            }
            .launchIn(viewModelScope)

        // Observe errors from the BluetoothController.
        // This flow emits any errors that occur during Bluetooth operations.
        // It's used to update the ViewModel's errorMessage state, which is then displayed to the user.
        bluetoothController
            .errors
            .onEach { error -> _state.update { it.copy(errorMessage = error) } }
            .launchIn(viewModelScope)
    }

    /**
     * Processes incoming [BluetoothIntent]s from the UI.
     *
     * This method acts as the entry point for all user actions. It performs:
     * 1. Guard checks for [BluetoothIntent.ConnectToDevice]:
     *    - Prevents multiple concurrent connection attempts.
     *    - Ignores connection requests to an already connected device.
     *    - Handles requests to connect to a new device while already connected to another by
     *      first initiating a disconnect (the user might need to trigger the connect intent again).
     * 2. Updates the [_state] by calling the [reduce] function with the current state and the intent.
     *    This step typically handles immediate UI state changes like setting `isConnecting`.
     * 3. Triggers side effects based on the intent, such as:
     *    - Calling [connectToDevice] for connection requests.
     *    - Calling [disconnectFromDevice] for disconnection requests.
     *    - Calling [startScan] or [stopScan] for discovery actions.
     *    - Calling [sendMessage] for sending OBD2 commands (though this part uses a deprecated API).
     *    - Clearing error messages.
     *
     * @param intent The [BluetoothIntent] representing the user's action.
     */
    fun processIntent(intent: BluetoothIntent) {

        if (intent is BluetoothIntent.ConnectToDevice) {
            // Guard 1: Already attempting to connect to any device
            if (_state.value.isConnecting) {
                Log.d(
                    "BluetoothViewModel",
                    "Connection attempt already in progress for ${_state.value.connectedDeviceAddress}. New attempt to ${intent.device.address} ignored."
                )
                return
            }
            // Guard 2: Already connected to the *same* device
            if (_state.value.isConnected && _state.value.connectedDeviceAddress == intent.device.address) {
                Log.d(
                    "BluetoothViewModel",
                    "Already connected to ${intent.device.address}. Intent to connect to same device ignored."
                )
                return
            }
            // Guard 3: Connected to a *different* device, so disconnect first
            if (_state.value.isConnected && _state.value.connectedDeviceAddress != intent.device.address) {
                Log.d(
                    "BluetoothViewModel",
                    "Currently connected to ${_state.value.connectedDeviceAddress}. Disconnecting before attempting connection to ${intent.device.address}."
                )
                disconnectFromDevice() // This will change state, leading to UI update. User might need to click again.
                // To automatically proceed, one might queue the intent or use a more complex state, for now, manual re-click is implied.
                return // Stop processing this intent now, let disconnect proceed.
            }
        }

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

    /**
     * A pure reducer function that computes the new [BluetoothState] based on the current state and
     * a given [BluetoothIntent].
     *
     * This function is responsible for immediate, synchronous state transformations that don't involve
     * side effects. For example, when a [BluetoothIntent.ConnectToDevice] intent is received,
     * this reducer will update the state to set `isConnecting = true` and store the
     * `connectedDeviceAddress`.
     *
     * More complex operations or those with side effects (like actually initiating a Bluetooth connection)
     * are handled separately after this reducer has updated the state.
     *
     * @param currentState The current [BluetoothState] of the ViewModel.
     * @param intent The [BluetoothIntent] to process.
     * @return The new [BluetoothState] after applying the intent.
     */
    private fun reduce(currentState: BluetoothState, intent: BluetoothIntent): BluetoothState {
        return when (intent) {
            is BluetoothIntent.ConnectToDevice -> currentState.copy(
                isConnecting = true,
                connectedDeviceAddress = intent.device.address,
                errorMessage = null
            )

            is BluetoothIntent.DisconnectFromDevice -> currentState.copy(
                isConnecting = false,
                isConnected = false,
                connectedDeviceAddress = null
            )

            is BluetoothIntent.StartScan -> currentState
            is BluetoothIntent.StopScan -> currentState
            is BluetoothIntent.SendCommand -> currentState
            is BluetoothIntent.DismissError -> currentState.copy(errorMessage = null)
        }
    }

    /**
     * Initiates a connection to the specified [BluetoothDeviceDomain].
     *
     * This function is called as a side effect of processing a [BluetoothIntent.ConnectToDevice].
     * It invokes `bluetoothController.connectToDevice(device)` which returns a [Flow] of
     * [ConnectionResult]. This flow is then listened to by the [listen] extension function.
     * The [deviceConnectionJob] is assigned the job returned by `listen()`, allowing the
     * connection attempt to be cancelled (e.g., by [disconnectFromDevice]).
     *
     * The UI state `isConnecting` should have already been set to true by the `reduce` function
     * before this method is called.
     *
     * @param device The [BluetoothDeviceDomain] to connect to.
     */
    private fun connectToDevice(device: BluetoothDeviceDomain) {
        Log.d(
            "BluetoothViewModel",
            "connectToDevice called for device: ${device.address}. CurrentVMState: (conn=${_state.value.isConnected}, connecting=${_state.value.isConnecting}, addr=${_state.value.connectedDeviceAddress})"
        )
        deviceConnectionJob = bluetoothController.connectToDevice(device).listen()
    }

    /**
     * Disconnects from the currently connected device.
     *
     * This function performs two main actions:
     * 1. Cancels any ongoing [deviceConnectionJob]. This is important if a connection attempt
     *    is still in progress (e.g., in the `awaitCancellation` stage of the controller's
     *    `connectToDevice` flow) or if it's an active connection flow that needs to be terminated.
     * 2. Calls `bluetoothController.closeConnection()` to ensure the underlying Bluetooth socket
     *    and any associated services (like [OBD2BluetoothService]) are properly closed and cleaned up.
     *
     * The UI state (e.g., `isConnected = false`, `isConnecting = false`) is typically updated
     * via the `reduce` function when [BluetoothIntent.DisconnectFromDevice] is processed, or
     * through the observation of `bluetoothController.isConnected` changing to `false`.
     */
    private fun disconnectFromDevice() {
        Log.d(
            "BluetoothViewModel",
            "disconnectFromDevice called. CurrentVMState: (conn=${_state.value.isConnected}, connecting=${_state.value.isConnecting}, addr=${_state.value.connectedDeviceAddress})"
        )
        deviceConnectionJob?.cancel()
        bluetoothController.closeConnection()
        // After explicitly calling close, ensure the state reflects this immediately if not already handled by controller's isConnected flow.
        // However, the controller's isConnected flow should be the primary driver for these state changes.
        // _state.update { it.copy(isConnected = false, isConnecting = false, connectedDeviceAddress = null) }
    }

    /**
     * Sends an OBD2 command to the connected device.
     *
     * This function is called as a side effect of processing a [BluetoothIntent.SendCommand].
     * It invokes `bluetoothController.sendOBD2Command(message)` which returns an [OBD2Message].
     * If the message is not null, it converts the [OBD2Message] to a [BluetoothMessage] and
     * updates the [_state] with the new message.
     *
     * @param message The OBD2 command to send.
     */
    private fun sendMessage(message: String) {
        viewModelScope.launch {
            val obd2Service = bluetoothController.getObd2Service()
            if (obd2Service == null) {
                Log.e(
                    "BluetoothViewModel",
                    "sendMessage: OBD2BluetoothService is null, cannot send command."
                )
                _state.update { it.copy(errorMessage = "Not connected or OBD2 service not ready.") }
                return@launch
            }

            try {
                // Determine if it's an AT command or OBD2 command
                // Basic heuristic: AT commands start with "AT"
                val isAtCommand = message.uppercase().startsWith("AT")

                // If sending an AT command, add a delay to ensure the adapter has time to process
                // This is especially important right after establishing a connection
                // if (isAtCommand) {
                //     delay(2000) // 2-second delay before sending AT commands
                // }

                val responseFlow = if (isAtCommand) {
                    obd2Service.executeAtCommand(message)
                } else {
                    obd2Service.executeOBD2Command(message)
                }

                // Collect the first response, or null if flow is empty (e.g., timeout before any emission)
                val obd2Response = responseFlow.firstOrNull()

                if (obd2Response != null) {
                    val bluetoothMessage = OBD2MessageMapper.convertToBluetoothMessage(obd2Response)
                    _state.update { it.copy(messages = it.messages + bluetoothMessage) }
                    if (obd2Response.isError) {
                        _state.update { it.copy(errorMessage = "Command Error: ${obd2Response.decodedValue}") }
                    }
                } else {
                    Log.w(
                        "BluetoothViewModel",
                        "sendMessage: No response received for command: $message"
                    )
                    _state.update { it.copy(errorMessage = "No response from adapter for: $message") }
                }
            } catch (e: Exception) {
                Log.e(
                    "BluetoothViewModel",
                    "sendMessage: Error sending command '$message': ${e.message}",
                    e
                )
                _state.update { it.copy(errorMessage = "Error sending command: ${e.message}") }
            }
        }
    }

    /**
     * Starts a Bluetooth device scan.
     *
     * This function is called as a side effect of processing a [BluetoothIntent.StartScan].
     * It invokes `bluetoothController.startDiscovery()` to start scanning for nearby Bluetooth devices.
     */
    private fun startScan() {
        bluetoothController.startDiscovery()
    }

    /**
     * Stops a Bluetooth device scan.
     *
     * This function is called as a side effect of processing a [BluetoothIntent.StopScan].
     * It invokes `bluetoothController.stopDiscovery()` to stop scanning for nearby Bluetooth devices.
     */
    private fun stopScan() {
        bluetoothController.stopDiscovery()
    }

    /**
     * Extension function to collect [ConnectionResult]s from the [BluetoothController.connectToDevice] flow
     * and update the ViewModel's [BluetoothState] accordingly.
     *
     * This function handles the different outcomes of a connection attempt:
     * - [ConnectionResult.ConnectionEstablished]: Initially, this might only indicate the socket is connected.
     *   The ViewModel sets `isConnecting = true` here, as the full OBD2 service initialization
     *   is handled by the controller. The final `isConnected = true` state is set when
     *   `bluetoothController.isConnected` emits `true`.
     * - [ConnectionResult.TransferSucceeded]: This indicates that data is being received, implying
     *   a fully established and operational connection (including OBD2 service initialization).
     *   The ViewModel updates its state to `isConnected = true`, `isConnecting = false`, and appends
     *   the received message. (Note: This specific `TransferSucceeded` case might be from an older
     *   design; modern `connectToDevice` might not emit it if it relies on `awaitCancellation` and
     *   a separate message flow from `OBD2BluetoothService`).
     * - [ConnectionResult.Error]: Updates the state with the error message and sets `isConnected`
     *   and `isConnecting` to `false`. It also ensures `bluetoothController.closeConnection()` is called.
     *
     * Catches any exceptions from the flow, updates the state with an error message, and ensures
     * the connection is closed. The collection is launched in the [viewModelScope].
     *
     * @return The [Job] associated with collecting this flow, allowing for cancellation.
     */
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

    /**
     * Releases the Bluetooth controller when the ViewModel is cleared.
     *
     * This function is called when the ViewModel is no longer needed. It ensures that the
     * Bluetooth controller is properly released and resources are cleaned up.
     */
    override fun onCleared() {
        super.onCleared()
        bluetoothController.release()
    }
}
