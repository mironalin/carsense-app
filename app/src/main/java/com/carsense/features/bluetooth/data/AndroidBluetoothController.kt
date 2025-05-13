package com.carsense.features.bluetooth.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import com.carsense.features.bluetooth.domain.BluetoothController
import com.carsense.features.bluetooth.domain.BluetoothDeviceDomain
import com.carsense.features.bluetooth.domain.ConnectionResult
import com.carsense.features.obd2.data.OBD2BluetoothService
import com.carsense.features.obd2.domain.OBD2Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import java.io.IOException
import java.util.UUID

/**
 * Android-specific implementation of the [BluetoothController] interface.
 *
 * Manages Bluetooth device discovery (scanning), pairing, and connection establishment. It handles
 * the lifecycle of Bluetooth operations, including permission checks for necessary Bluetooth
 * permissions (e.g., SCAN, CONNECT).
 *
 * This controller is responsible for:
 * - Discovering nearby Bluetooth devices.
 * - Listing paired Bluetooth devices.
 * - Establishing a RFCOMM connection to a selected [BluetoothDeviceDomain].
 * - Creating and initializing an [OBD2BluetoothService] instance upon successful connection to
 * handle OBD2-specific communication.
 * - Managing the connection state ([isConnected]) and providing flows for scanned/paired devices
 * and error messages.
 * - Releasing resources when Bluetooth operations are no longer needed.
 *
 * It uses [BluetoothStateReceiver] and [FoundDeviceReceiver] to react to system Bluetooth events.
 *
 * @param context The Android [Context] required for accessing Bluetooth services and registering
 * receivers.
 */
@SuppressLint("MissingPermission")
class AndroidBluetoothController(private val context: Context) : BluetoothController {

    private val tag = "bluetoothController"
    private val bluetoothManager by lazy { context.getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    private var dataTransferService: OBD2BluetoothService? = null

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean>
        get() = _isConnected.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val scannedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _scannedDevices.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceDomain>>(emptyList())
    override val pairedDevices: StateFlow<List<BluetoothDeviceDomain>>
        get() = _pairedDevices.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    override val errors: SharedFlow<String>
        get() = _errors.asSharedFlow()

    private val foundDeviceReceiver = FoundDeviceReceiver { device ->
        _scannedDevices.update { devices ->
            val newDevice = device.toBluetoothDeviceDomain()
            if (newDevice in devices) devices else devices + newDevice
        }
    }

    /**
     * Bluetooth state receiver.
     *
     * This receiver is used to update the [_isConnected] state flow when the Bluetooth connection
     * state changes.
     */
    private val bluetoothStateReceiver =
        BluetoothStateReceiver { receiverIsConnected, bluetoothDevice ->
            if (bluetoothAdapter?.bondedDevices?.contains(bluetoothDevice) == true) {
                // Only update from receiver if it's a disconnect event.
                // Connection status (true) should be set by the explicit connectToDevice flow
                // success.
                if (!receiverIsConnected) {
                    Log.d(
                        tag,
                        "BluetoothStateReceiver received disconnect for bonded device: ${bluetoothDevice.address}. Updating _isConnected to false."
                    )
                    _isConnected.update { false }
                }
            } else {
                // For non-bonded devices, we generally don't manage global connection state via
                // receiver.
                // If it was a non-bonded device that got an ACL_CONNECTED/DISCONNECTED,
                // it's not one we are maintaining a persistent connection state for via
                // _isConnected.
                Log.d(
                    tag,
                    "BluetoothStateReceiver event for non-bonded device ${bluetoothDevice.address}, isConnected event ignored for global state."
                )
            }
        }

    private var currentServerSocket: BluetoothServerSocket? = null
    private var currentClientSocket: BluetoothSocket? = null

    /**
     * Initializes the Bluetooth controller.
     *
     * This method is used to initialize the Bluetooth controller. It is called when the controller
     * is created.
     */
    init {
        updatePairedDevices()
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
        )
    }

    /**
     * Starts the Bluetooth device discovery.
     *
     * This method is used to start the Bluetooth device discovery. It is called when the discovery
     * is needed.
     */
    override fun startDiscovery() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        context.registerReceiver(foundDeviceReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        updatePairedDevices()

        bluetoothAdapter?.startDiscovery()
    }

    /**
     * Stops the Bluetooth device discovery.
     *
     * This method is used to stop the Bluetooth device discovery. It is called when the discovery
     * is no longer needed.
     */
    override fun stopDiscovery() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        bluetoothAdapter?.cancelDiscovery()
    }

    /**
     * Attempts to establish a connection to the specified Bluetooth device and initialize an OBD2
     * service.
     *
     * This function orchestrates the entire connection process:
     * 1. Checks for [Manifest.permission.BLUETOOTH_CONNECT] permission.
     * 2. Stops any ongoing Bluetooth discovery.
     * 3. Retrieves the [BluetoothDevice] instance using its address.
     * 4. Attempts to create an RFCOMM [BluetoothSocket] using the standard SPP UUID.
     * 5. Calls [connectWithTimeout] to connect the socket.
     * 6. If successful, creates an [OBD2BluetoothService] instance.
     * 7. Calls [OBD2BluetoothService.initialize()] to set up the OBD2 adapter.
     * 8. If SPP UUID connection or initialization fails, it attempts a fallback connection method
     * ```
     *    ([createFallbackSocket]) and repeats steps 5-7.
     * ```
     * 9. Upon successful connection and OBD2 service initialization, it updates [_isConnected] to
     * `true`
     * ```
     *    and emits [ConnectionResult.ConnectionEstablished]. The flow then suspends using
     *    `kotlinx.coroutines.awaitCancellation()` to keep the connection alive.
     * ```
     * 10. If any step fails irrecoverably, it emits [ConnectionResult.Error] and ensures resources
     * are cleaned up.
     *
     * The flow runs on [Dispatchers.IO]. An `onCompletion` block ensures [closeConnection] is
     * called when the flow terminates (either by cancellation or completion). Errors are caught and
     * emitted.
     *
     * @param device The [BluetoothDeviceDomain] object representing the target device.
     * @return A [Flow] of [ConnectionResult] indicating the outcome:
     * ```
     *         - [ConnectionResult.ConnectionEstablished] on success.
     *         - [ConnectionResult.Error] with a message on failure.
     *         The flow will remain suspended on success until externally cancelled, at which point
     *         `onCompletion` triggers cleanup.
     * ```
     */
    override fun connectToDevice(device: BluetoothDeviceDomain): Flow<ConnectionResult> {
        return flow {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                throw SecurityException("No BLUETOOTH_CONNECT permission")
            }

            // Make sure discovery is stopped before trying to connect
            stopDiscovery()

            // Get the remote device
            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
            if (bluetoothDevice == null) {
                Log.e(tag, "Device not found: ${device.address}")
                emit(ConnectionResult.Error("Device not found"))
                return@flow
            }

            Log.d(tag, "Connecting to device: ${device.name} (${device.address})")

            // Try with the ELM327/OBD standard UUID first
            try {
                Log.d(tag, "Attempting connection with SPP UUID")
                currentClientSocket =
                    bluetoothDevice.createRfcommSocketToServiceRecord(
                        UUID.fromString(SPP_UUID)
                    )

                // Connect with timeout
                connectWithTimeout(currentClientSocket!!, 5000)

                Log.d(tag, "Connection established to: ${device.address}")
                emit(ConnectionResult.ConnectionEstablished)

                // Create data transfer service
                val service = OBD2BluetoothService(currentClientSocket!!, context)
                dataTransferService = service

                // Initialize the OBD2 connection first
                val initSuccess = service.initialize()
                if (!initSuccess) {
                    Log.e(tag, "Failed to initialize OBD2 device")
                    emit(ConnectionResult.Error("Failed to initialize OBD2 device"))
                    currentClientSocket?.close()
                    currentClientSocket = null
                    dataTransferService = null
                    _isConnected.value = false
                    return@flow
                }

                // Connection and initialization successful.
                // The service is now ready via getObd2Service().
                // The flow should remain active to keep the connection open until
                // explicitly closed.
                // We don't emitAll from a finishing flow anymore.
                _isConnected.value = true
                Log.d(tag, "OBD2 Service initialized and ready.")

                // Keep the flow suspended (and connection open) until cancelled externally
                // This allows the caller to decide when to close via closeConnection() or
                // release()
                kotlinx.coroutines.awaitCancellation()
            } catch (e: IOException) {
                Log.e(tag, "First connection attempt failed: ${e.message}")
                currentClientSocket?.close()

                // Try second attempt with reflection method if first failed
                try {
                    Log.d(tag, "Attempting fallback connection method")
                    currentClientSocket = createFallbackSocket(bluetoothDevice)

                    // Connect with timeout
                    connectWithTimeout(currentClientSocket!!, 5000)

                    Log.d(tag, "Fallback connection established to: ${device.address}")
                    emit(ConnectionResult.ConnectionEstablished)

                    // Create data transfer service
                    val service = OBD2BluetoothService(currentClientSocket!!, context)
                    dataTransferService = service

                    // Initialize the OBD2 connection first
                    val initSuccess = service.initialize()
                    if (!initSuccess) {
                        Log.e(tag, "Failed to initialize OBD2 device (fallback)")
                        emit(
                            ConnectionResult.Error(
                                "Failed to initialize OBD2 device (fallback)"
                            )
                        )
                        currentClientSocket?.close()
                        currentClientSocket = null
                        dataTransferService = null
                        _isConnected.value = false
                        return@flow
                    }

                    _isConnected.value = true
                    Log.d(tag, "OBD2 Service initialized via fallback and ready.")
                    kotlinx.coroutines.awaitCancellation()
                } catch (e2: Exception) {
                    Log.e(tag, "Fallback connection failed: ${e2.message}")
                    currentClientSocket?.close()
                    currentClientSocket = null
                    emit(ConnectionResult.Error("Failed to connect: ${e2.message}"))
                }
            }
        }
            .onCompletion {
                Log.d(tag, "Connection flow completed")
                closeConnection()
            }
            .catch { e ->
                Log.e(tag, "Connection error: ${e.message}")
                emit(ConnectionResult.Error("Connection error: ${e.message}"))
            }
            .flowOn(Dispatchers.IO)
    }

    /**
     * Attempts to connect a [BluetoothSocket] with a specified timeout.
     *
     * This is a suspend function that repeatedly tries to call `socket.connect()` in a loop until
     * the connection is successful or the `timeoutMs` duration is exceeded. A short delay is
     * introduced between connection attempts within the timeout period to prevent aggressive,
     * immediate retries that might overwhelm the Bluetooth stack or device.
     *
     * @param socket The [BluetoothSocket] to connect.
     * @param timeoutMs The maximum time in milliseconds to attempt the connection.
     * @throws IOException if the socket is already closed or if the connection fails after the
     * timeout.
     * @throws SecurityException if [Manifest.permission.BLUETOOTH_CONNECT] is missing (though
     * typically `socket.connect()` itself would throw this).
     */
    private suspend fun connectWithTimeout(socket: BluetoothSocket, timeoutMs: Int) {
        var connected = false
        val startTime = System.currentTimeMillis()

        while (!connected && System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                socket.connect()
                connected = true

                // Give the connection a moment to stabilize
                delay(500)
            } catch (e: IOException) {
                // If we're not out of time yet, try again
                if (System.currentTimeMillis() - startTime < timeoutMs) {
                    Log.d(tag, "Connection attempt failed, retrying...")
                    delay(500)
                } else {
                    throw e
                }
            }
        }

        if (!connected) {
            throw IOException("Connection timeout")
        }
    }

    /**
     * Attempts to create a [BluetoothSocket] using a fallback reflection method.
     *
     * This method is used if the standard `device.createRfcommSocketToServiceRecord(SPP_UUID)`
     * fails. Some older or non-standard Bluetooth devices might require creating a socket on a
     * specific RFCOMM channel (typically channel 1) using reflection to access a hidden
     * `createRfcommSocket` method.
     *
     * @param device The [BluetoothDevice] to create the socket for.
     * @return A connected [BluetoothSocket] if the fallback method is successful, `null` otherwise.
     * @throws SecurityException if [Manifest.permission.BLUETOOTH_CONNECT] is missing when calling
     * connect.
     * @throws IOException if the connection attempt fails.
     * @throws NoSuchMethodException if the reflection method is not found.
     * @throws Exception for other reflection-related errors.
     */
    @SuppressLint("DiscouragedPrivateApi")
    private fun createFallbackSocket(device: BluetoothDevice): BluetoothSocket {
        try {
            // Try to use reflection to get a socket
            val method =
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            return method.invoke(device, 1) as BluetoothSocket
        } catch (e: Exception) {
            Log.e(tag, "Fallback socket creation failed: ${e.message}")
            throw IOException("Could not create socket via reflection")
        }
    }

    /**
     * Retrieves the currently active [OBD2BluetoothService] instance.
     *
     * This service is only available after a successful connection and initialization sequence
     * completed by [connectToDevice]. Callers should typically check [isConnected] or observe the
     * connection flow before attempting to retrieve and use this service.
     *
     * @return The initialized [OBD2BluetoothService] if a connection is active and the service has
     * been set up, `null` otherwise (e.g., if not connected, or if connection is in progress, or if
     * an error occurred during setup).
     */
    override fun getObd2Service(): OBD2BluetoothService? {
        return if (dataTransferService?.isReady() == true) dataTransferService else null
    }

    /**
     * @deprecated Use [getObd2Service] and then [OBD2BluetoothService.executeOBD2Command] or
     * [OBD2BluetoothService.executeAtCommand]. This method is deprecated due to the underlying
     * service changes and its problematic raw string handling.
     */
    @Deprecated(
        "Use getObd2Service() and then service.executeOBD2Command() or service.executeAtCommand().",
        ReplaceWith(
            "getObd2Service()?.executeAtCommand(command) or getObd2Service()?.executeOBD2Command(typedCommand)"
        )
    )
    override suspend fun sendOBD2Command(command: String): OBD2Message? {
        Log.w(tag, "sendOBD2Command(String) is deprecated and will likely not work as expected.")
        // The underlying service.sendCommand and service.getLastRawResponse are gone/deprecated.
        // This method can't function as it did.
        // To maintain some semblance of sending, we can try executeAtCommand and take the first
        // response,
        // then convert it to the old OBD2Message. This is NOT recommended for PID commands.

        val service = dataTransferService
        if (service == null) {
            Log.e(tag, "DataTransferService is null, cannot send command: $command")
            return OBD2Message.createResponse("Error: Service not available", true)
        }

        // This is a rough adaptation and might not be fully correct for all old use cases.
        // It assumes the caller wants a single response.
        var obd2Response: com.carsense.features.obd2.data.OBD2Response? = null
        try {
            service.executeAtCommand(command).collect {
                obd2Response = it
                // Typically, we'd break after the first relevant emission for a single
                // command-response pattern.
                // However, the flow from executeCommand might emit multiple things (e.g. errors
                // then data, or just one)
                // For this deprecated method, just taking the last emission before flow completion
                // (or first if collected with .firstOrNull())
                // For simplicity here, we are just collecting and overriding. This part is tricky
                // for a deprecated method.
                if (!it.isError || it.decodedValue.isNotEmpty()
                ) { // Stop if we get a non-error or a specific error message
                    // This is a flawed attempt to mimic old behavior; proper collection is needed
                    // by new callers.
                    throw kotlinx.coroutines.CancellationException(
                        "Collected one response for deprecated method"
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Expected for stopping collection
        } catch (e: Exception) {
            Log.e(
                tag,
                "Error collecting from executeAtCommand for deprecated sendOBD2Command: $command",
                e
            )
            return OBD2Message.createResponse("Error executing command: ${e.message}", true)
        }

        return if (obd2Response != null) {
            OBD2Message.createResponse(
                response = "${obd2Response!!.command}: ${obd2Response!!.decodedValue}", // Mimic
                // old
                // format
                isError = obd2Response!!.isError
            )
        } else {
            OBD2Message.createResponse("No response or error for command: $command", true)
        }
    }

    /**
     * Initializes the OBD2 service.
     *
     * This method is used to initialize the OBD2 service. It is called when the connection is
     * established.
     *
     * @return `true` if the OBD2 service is initialized, `false` otherwise.
     */
    override suspend fun initializeOBD2(): Boolean {
        return dataTransferService?.initialize() ?: false
    }

    /**
     * Closes the active Bluetooth connection and associated services.
     *
     * This method performs the following cleanup steps:
     * 1. Logs the intention to close the connection.
     * 2. Calls `close()` on the [dataTransferService] (which is the [OBD2BluetoothService])
     * ```
     *    to allow it to release its resources (streams, etc.).
     * ```
     * 3. Sets [dataTransferService] to `null`.
     * 4. Closes the [currentClientSocket] if it's open.
     * 5. Sets [currentClientSocket] to `null`.
     * 6. Updates the [_isConnected] state flow to `false`.
     *
     * This method is typically called when the connection is no longer needed, either explicitly by
     * the user/application or automatically by the `onCompletion` block of the [connectToDevice]
     * flow. It ensures that all Bluetooth resources are properly released.
     */
    override fun closeConnection() {
        Log.d(
            tag,
            "AndroidBluetoothController.closeConnection() called. Current isConnected: ${_isConnected.value}"
        )
        dataTransferService?.close()

        try {
            currentClientSocket?.close()
        } catch (e: IOException) {
            Log.e(tag, "Error closing client socket: ${e.message}")
        }

        try {
            currentServerSocket?.close()
        } catch (e: IOException) {
            Log.e(tag, "Error closing server socket: ${e.message}")
        }

        currentClientSocket = null
        currentServerSocket = null
        dataTransferService = null

        _isConnected.value = false
    }

    /**
     * Releases all resources held by the Bluetooth controller.
     *
     * This method should be called when the controller is no longer needed (e.g., in `onDestroy` of
     * an Android component) to prevent resource leaks. It performs the following actions:
     * 1. Unregisters the [foundDeviceReceiver] and [bluetoothStateReceiver].
     * 2. Calls [closeConnection] to ensure any active connection and OBD2 service are shut down.
     * 3. Closes the [currentServerSocket] if it was opened (though server socket usage seems
     * minimal in the current OBD2 client implementation). This provides a comprehensive cleanup of
     * all Bluetooth-related resources managed by this controller.
     */
    override fun release() {
        Log.d(tag, "Releasing Bluetooth controller")
        closeConnection()
        try {
            context.unregisterReceiver(foundDeviceReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(tag, "Error unregistering foundDeviceReceiver: ${e.message}")
        }

        try {
            context.unregisterReceiver(bluetoothStateReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(tag, "Error unregistering bluetoothStateReceiver: ${e.message}")
        }
    }

    /**
     * Updates the list of paired Bluetooth devices.
     *
     * This method checks for [Manifest.permission.BLUETOOTH_CONNECT] permission and then maps the
     * bonded devices to [BluetoothDeviceDomain] objects. It updates the [_pairedDevices] state flow
     * with the new list.
     *
     * This is used by the [updatePairedDevices] method to ensure the UI reflects the correct list
     * of paired devices.
     */
    private fun updatePairedDevices() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }
        bluetoothAdapter?.bondedDevices?.map { it.toBluetoothDeviceDomain() }?.also { devices ->
            _pairedDevices.update { devices }
        }
    }

    /**
     * Checks if the application has the specified permission.
     *
     * This method is used to verify if the application has the necessary permissions before
     * performing Bluetooth operations.
     *
     * @param permission The permission to check for (e.g., [Manifest.permission.BLUETOOTH_SCAN] or
     * [Manifest.permission.BLUETOOTH_CONNECT]).
     * @return `true` if the permission is granted, `false` otherwise.
     */
    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Standard Serial Port Profile UUID for OBD2 adapters.
     *
     * This is the UUID used for the Serial Port Profile (SPP) connection to OBD2 adapters. It is
     * specifically for ELM327 and other OBD2 adapters.
     */
    companion object {
        const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    }
}
