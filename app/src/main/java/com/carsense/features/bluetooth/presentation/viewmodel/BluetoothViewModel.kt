package com.carsense.features.bluetooth.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.features.bluetooth.domain.BluetoothController
import com.carsense.features.bluetooth.domain.BluetoothDeviceDomain
import com.carsense.features.bluetooth.domain.ConnectionResult
import com.carsense.features.bluetooth.presentation.intent.BluetoothIntent
import com.carsense.features.bluetooth.presentation.model.BluetoothState
import com.carsense.features.diagnostics.data.DiagnosticSessionManager
import com.carsense.features.diagnostics.domain.usecase.CreateDiagnosticAfterConnectionUseCase
import com.carsense.features.location.domain.service.DiagnosticLocationService
import com.carsense.features.obd2.domain.OBD2MessageMapper
import com.carsense.features.vehicles.domain.repository.VehicleRepository
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
class BluetoothViewModel @Inject constructor(
    private val bluetoothController: BluetoothController,
    private val vehicleRepository: VehicleRepository,
    private val createDiagnosticAfterConnectionUseCase: CreateDiagnosticAfterConnectionUseCase,
    private val diagnosticSessionManager: DiagnosticSessionManager,
    private val diagnosticLocationService: DiagnosticLocationService
) : ViewModel() {

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
                        // We successfully connected, set state for connection completion
                        // We'll use this to navigate to the mileage input screen
                        currentState.copy(
                            isConnected = true,
                            isConnecting = false, // The full connection, including OBD2 init, is complete.
                            errorMessage = null,
                            connectionCompleted = !currentState.isConnected // Set true only on first connection
                        )
                    } else {
                        // Remove LocationTracker usage - MainActivity will handle this via ForegroundLocationService

                        // If we were in the middle of a connection attempt that now failed/dropped.
                        currentState.copy(
                            isConnected = false,
                            isConnecting = if (currentState.isConnecting && currentState.connectedDeviceAddress != null) false else currentState.isConnecting,
                            // Clear target device if connection attempt failed/dropped
                            connectedDeviceAddress = if (currentState.isConnecting) null else currentState.connectedDeviceAddress,
                            // Reset diagnostic creation state
                            diagnosticCreationInProgress = false,
                            diagnosticUuid = null,
                            connectionCompleted = false
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
        // First update state (synchronous)
        _state.update { currentState -> reduce(currentState, intent) }

        // Then handle side effects
        when (intent) {
            is BluetoothIntent.ConnectToDevice -> connectToDevice(intent.device)
            is BluetoothIntent.DisconnectFromDevice -> disconnectFromDevice()
            is BluetoothIntent.StartScan -> startScan()
            is BluetoothIntent.StopScan -> stopScan()
            is BluetoothIntent.SendCommand -> sendMessage(intent.message)
            is BluetoothIntent.DismissError -> _state.update { it.copy(errorMessage = null) }
            is BluetoothIntent.SubmitOdometerReading -> createDiagnostic(intent.odometer)
            is BluetoothIntent.ClearMessages -> _state.update { it.copy(messages = emptyList()) }
            is BluetoothIntent.SendCommandWithPrompt -> sendMessage(intent.message)
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
                connectedDeviceAddress = null,
                diagnosticCreationInProgress = false,
                diagnosticUuid = null
            )

            is BluetoothIntent.StartScan -> currentState
            is BluetoothIntent.StopScan -> currentState
            is BluetoothIntent.SendCommand -> currentState
            is BluetoothIntent.DismissError -> currentState.copy(errorMessage = null)
            is BluetoothIntent.SubmitOdometerReading -> currentState.copy(
                diagnosticCreationInProgress = true
            )

            is BluetoothIntent.ClearMessages -> currentState.copy(messages = emptyList())
            is BluetoothIntent.SendCommandWithPrompt -> currentState
        }
    }

    /**
     * Creates a diagnostic record after the user submits the odometer reading
     * @param odometer The vehicle's current odometer reading
     */
    private fun createDiagnostic(odometer: Int) {
        Log.d(
            "BluetoothViewModel",
            "Creating diagnostic with odometer reading: $odometer, isConnected: ${_state.value.isConnected}"
        )

        viewModelScope.launch {
            try {
                // Start diagnostic creation in progress
                _state.update { it.copy(diagnosticCreationInProgress = true) }
                Log.d(
                    "BluetoothViewModel",
                    "Diagnostic creation in progress, starting use case execution"
                )

                // Execute the use case to create the diagnostic
                createDiagnosticAfterConnectionUseCase.execute(odometer)
                    .collect { result ->
                        Log.d(
                            "BluetoothViewModel",
                            "Received result from createDiagnosticAfterConnectionUseCase"
                        )
                        result.fold(
                            onSuccess = { diagnostic ->
                                // Store the diagnostic UUID for reference and mark creation as complete
                                _state.update {
                                    it.copy(
                                        diagnosticCreationInProgress = false,
                                        diagnosticUuid = diagnostic.uuid
                                    )
                                }

                                // Also store the UUID in the session manager
                                diagnosticSessionManager.setCurrentDiagnosticUUID(diagnostic.uuid)

                                Log.d(
                                    "BluetoothViewModel",
                                    "Diagnostic created successfully with UUID: ${diagnostic.uuid}"
                                )

                                // Start location tracking for this diagnostic session
                                startLocationTrackingForDiagnostic(
                                    diagnostic.uuid,
                                    diagnostic.vehicleUuid
                                )
                            },
                            onFailure = { error ->
                                // Update state with error and mark creation as failed
                                _state.update {
                                    it.copy(
                                        diagnosticCreationInProgress = false,
                                        errorMessage = "Failed to create diagnostic: ${error.message}"
                                    )
                                }
                                Log.e(
                                    "BluetoothViewModel",
                                    "Failed to create diagnostic: ${error.message}",
                                    error
                                )
                            }
                        )
                    }
            } catch (e: Exception) {
                // Handle any unexpected exceptions
                _state.update {
                    it.copy(
                        diagnosticCreationInProgress = false,
                        errorMessage = "Error creating diagnostic: ${e.message}"
                    )
                }
                Log.e("BluetoothViewModel", "Exception creating diagnostic: ${e.message}", e)
            }
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
     * Starts location tracking for a diagnostic session
     */
    private fun startLocationTrackingForDiagnostic(diagnosticUUID: String, vehicleUUID: String) {
        try {
            Log.d(
                "BluetoothViewModel",
                "Starting location tracking for diagnostic: $diagnosticUUID, vehicle: $vehicleUUID"
            )
            diagnosticLocationService.startLocationTrackingForDiagnostic(
                diagnosticUUID,
                vehicleUUID
            )
            Log.d("BluetoothViewModel", "Location tracking started successfully")
        } catch (e: Exception) {
            Log.e("BluetoothViewModel", "Error starting location tracking: ${e.message}", e)
            // Don't fail the diagnostic creation if location tracking fails
            _state.update { it.copy(errorMessage = "Warning: Location tracking could not be started: ${e.message}") }
        }
    }

    /**
     * Stops location tracking and uploads remaining locations
     */
    private suspend fun stopLocationTrackingAndUpload(): Boolean {
        return try {
            Log.d(
                "BluetoothViewModel",
                "Stopping location tracking and uploading remaining locations"
            )
            val success = diagnosticLocationService.stopLocationTrackingAndUpload()
            Log.d("BluetoothViewModel", "Location tracking stopped, upload success: $success")
            success
        } catch (e: Exception) {
            Log.e("BluetoothViewModel", "Error stopping location tracking: ${e.message}", e)
            false
        }
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

        // Stop location tracking and upload remaining locations before clearing session
        viewModelScope.launch {
            try {
                // Stop location tracking and upload remaining locations
                val uploadSuccess = stopLocationTrackingAndUpload()
                Log.d(
                    "BluetoothViewModel",
                    "Location upload completed with success: $uploadSuccess"
                )

                // Clear diagnostic UUID from session manager after location handling
                diagnosticSessionManager.clearCurrentDiagnosticUUID()
                Log.d("BluetoothViewModel", "Cleared diagnostic UUID from session manager")
            } catch (e: Exception) {
                Log.e("BluetoothViewModel", "Error during disconnect cleanup: ${e.message}", e)
                // Still try to clear the session even if location handling fails
                try {
                    diagnosticSessionManager.clearCurrentDiagnosticUUID()
                } catch (sessionError: Exception) {
                    Log.e(
                        "BluetoothViewModel",
                        "Error clearing diagnostic UUID: ${sessionError.message}",
                        sessionError
                    )
                }
            }
        }

        deviceConnectionJob?.cancel()
        bluetoothController.closeConnection()
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
     * Resets the connection completed flag after navigation
     */
    fun resetConnectionCompleted() {
        _state.update { it.copy(connectionCompleted = false) }
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
     * Helper function to get the currently selected vehicle
     */
    private suspend fun getSelectedVehicle(): com.carsense.features.vehicles.domain.model.Vehicle? {
        return try {
            vehicleRepository.getAllVehicles().firstOrNull()?.find { it.isSelected }
        } catch (e: Exception) {
            Log.e("BluetoothViewModel", "Error getting selected vehicle", e)
            null
        }
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
        // Remove LocationTracker call since we're not using it directly anymore
    }
}
