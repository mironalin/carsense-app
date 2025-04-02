package com.carsense.data.bluetooth

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
import com.carsense.data.obd2.OBD2BluetoothService
import com.carsense.domain.bluetooth.BluetoothController
import com.carsense.domain.bluetooth.BluetoothDeviceDomain
import com.carsense.domain.bluetooth.ConnectionResult
import com.carsense.domain.obd2.OBD2Message
import java.io.IOException
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Implementation of BluetoothController that manages Bluetooth connections and communication with
 * OBD2 devices.
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

    private val bluetoothStateReceiver = BluetoothStateReceiver { isConnected, bluetoothDevice ->
        if (bluetoothAdapter?.bondedDevices?.contains(bluetoothDevice) == true) {
            _isConnected.update { isConnected }
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                _errors.emit("Can't connect to a non-paired device.")
            }
        }
    }

    private var currentServerSocket: BluetoothServerSocket? = null
    private var currentClientSocket: BluetoothSocket? = null

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

    override fun startDiscovery() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        context.registerReceiver(foundDeviceReceiver, IntentFilter(BluetoothDevice.ACTION_FOUND))

        updatePairedDevices()

        bluetoothAdapter?.startDiscovery()
    }

    override fun stopDiscovery() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
            return
        }

        bluetoothAdapter?.cancelDiscovery()
    }

    override fun startBluetoothServer(): Flow<ConnectionResult> {
        return flow {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                throw SecurityException("No BLUETOOTH_CONNECT permission")
            }

            // Use the standard SPP UUID for Bluetooth serial devices
            currentServerSocket =
                bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
                    "OBD2_service",
                    UUID.fromString(SPP_UUID)
                )

            Log.d(tag, "Bluetooth server started, waiting for connections...")

            var shouldLoop = true
            while (shouldLoop) {
                currentClientSocket =
                    try {
                        currentServerSocket?.accept()
                    } catch (e: IOException) {
                        Log.e(tag, "Error accepting connection: ${e.message}")
                        shouldLoop = false
                        null
                    }

                if (currentClientSocket != null) {
                    Log.d(
                        tag,
                        "Connection accepted: ${currentClientSocket?.remoteDevice?.address}"
                    )
                    emit(ConnectionResult.ConnectionEstablished)

                    currentServerSocket?.close()
                    val service = OBD2BluetoothService(currentClientSocket!!, context)
                    dataTransferService = service

                    // Initialize OBD2 connection
                    val initSuccess = service.initialize()
                    if (!initSuccess) {
                        Log.e(tag, "Failed to initialize OBD2 device")
                        emit(ConnectionResult.Error("Failed to initialize OBD2 device"))
                        return@flow
                    }

                    emitAll(
                        service.listenForOBD2Messages().map {
                            ConnectionResult.TransferSucceeded(it)
                        }
                    )
                }
            }
        }
            .onCompletion {
                Log.d(tag, "Server connection completed")
                closeConnection()
            }
            .flowOn(Dispatchers.IO)
    }

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
                    return@flow
                }

                // Start listening for messages only after successful initialization
                emitAll(
                    service.listenForOBD2Messages().map {
                        ConnectionResult.TransferSucceeded(it)
                    }
                )
                return@flow
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
                        Log.e(tag, "Failed to initialize OBD2 device")
                        emit(ConnectionResult.Error("Failed to initialize OBD2 device"))
                        return@flow
                    }

                    // Start listening for messages only after successful initialization
                    emitAll(
                        service.listenForOBD2Messages().map {
                            ConnectionResult.TransferSucceeded(it)
                        }
                    )
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

    /** @see BluetoothController.sendOBD2Command */
    override suspend fun sendOBD2Command(command: String): OBD2Message? {
        if (command.isBlank()) {
            return null
        }

        Log.d(tag, "Sending OBD2 command: $command")
        val service = dataTransferService
        val obd2Message = OBD2Message.createCommand(command)

        return if (service != null && service.sendCommand(command)) {
            _isConnected.value = true
            obd2Message
        } else {
            Log.e(tag, "Failed to send command or service is null")
            _errors.emit("Failed to send command: $command")
            null
        }
    }

    /** @see BluetoothController.initializeOBD2 */
    override suspend fun initializeOBD2(): Boolean {
        return dataTransferService?.initialize() ?: false
    }

    override fun closeConnection() {
        Log.d(tag, "Closing Bluetooth connection")
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

    private fun updatePairedDevices() {
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return
        }
        bluetoothAdapter?.bondedDevices?.map { it.toBluetoothDeviceDomain() }?.also { devices ->
            _pairedDevices.update { devices }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        // Standard Serial Port Profile UUID - this is specifically for ELM327 and other OBD2
        // adapters
        const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    }
}
