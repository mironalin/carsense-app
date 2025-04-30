package com.carsense.features.obd2.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.carsense.features.obd2.domain.OBD2Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Service that handles communication with an OBD2 device over Bluetooth. Manages socket connection,
 * initialization, and message transfer.
 */
class OBD2BluetoothService(
    private val socket: BluetoothSocket,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "OBD2BluetoothService"
        private const val MAX_CONNECTION_ATTEMPTS = 3
        private const val CONNECTION_RETRY_DELAY = 1000L
    }

    private var obd2Service: OBD2Service? = null
    private var isInitialized = false

    private fun hasPermission(permission: String): Boolean {
        return context?.let {
            ContextCompat.checkSelfPermission(it, permission) == PackageManager.PERMISSION_GRANTED
        }
            ?: true // If context is null, assume permission is granted
    }

    @SuppressLint("MissingPermission")
    suspend fun initialize(): Boolean {
        // Check Bluetooth permission
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission")
            return false
        }

        return withContext(Dispatchers.IO) {
            for (attempt in 1..MAX_CONNECTION_ATTEMPTS) {
                try {
                    Log.d(TAG, "Starting OBD2 initialization (attempt $attempt)")

                    // Check socket connection state
                    if (!socket.isConnected) {
                        try {
                            // Permission has been checked at the beginning of the function
                            Log.d(TAG, "Socket not connected, attempting to connect")
                            socket.connect()
                            // Give it a moment to stabilize, but reduced time
                            delay(500)
                        } catch (e: IOException) {
                            Log.e(TAG, "Failed to connect socket: ${e.message}")

                            if (attempt < MAX_CONNECTION_ATTEMPTS) {
                                Log.d(TAG, "Will retry connection in ${CONNECTION_RETRY_DELAY}ms")
                                delay(CONNECTION_RETRY_DELAY)
                                continue
                            } else {
                                return@withContext false
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Security exception: Missing permission: ${e.message}")
                            return@withContext false
                        }
                    }

                    // Create a new OBD2Service
                    try {
                        // Explicitly handle SecurityException when accessing streams
                        val inputStream =
                            try {
                                socket.inputStream
                            } catch (e: SecurityException) {
                                Log.e(
                                    TAG,
                                    "Security exception getting input stream: ${e.message}"
                                )
                                return@withContext false
                            }

                        val outputStream =
                            try {
                                socket.outputStream
                            } catch (e: SecurityException) {
                                Log.e(
                                    TAG,
                                    "Security exception getting output stream: ${e.message}"
                                )
                                return@withContext false
                            }

                        if (inputStream != null && outputStream != null) {
                            obd2Service = OBD2Service(inputStream, outputStream)
                            Log.d(TAG, "OBD2Service created successfully")
                        } else {
                            Log.e(TAG, "Failed to get valid socket streams")
                            continue
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to get socket streams: ${e.message}")

                        if (attempt < MAX_CONNECTION_ATTEMPTS) {
                            Log.d(TAG, "Will retry getting streams in ${CONNECTION_RETRY_DELAY}ms")
                            delay(CONNECTION_RETRY_DELAY)
                            continue
                        } else {
                            return@withContext false
                        }
                    }

                    // Initialize the OBD2 adapter with reduced timeout
                    try {
                        // Wait less time before initializing
                        delay(800)

                        // Initial initialization
                        val initResult = obd2Service?.initialize() ?: false

                        if (initResult) {
                            Log.d(TAG, "OBD2 basic initialization successful")

                            // Apply additional performance optimizations to the adapter
                            try {
                                optimizeAdapterSettings()
                                Log.d(TAG, "Applied performance optimizations to OBD adapter")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to optimize adapter settings: ${e.message}")
                                // Continue anyway as basic initialization succeeded
                            }

                            isInitialized = true
                            return@withContext true
                        } else {
                            Log.e(TAG, "OBD2 initialization failed")

                            if (attempt < MAX_CONNECTION_ATTEMPTS) {
                                Log.d(
                                    TAG,
                                    "Will retry initialization in ${CONNECTION_RETRY_DELAY}ms"
                                )
                                delay(CONNECTION_RETRY_DELAY)
                                continue
                            } else {
                                return@withContext false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "OBD2 initialization error: ${e.message}")

                        if (attempt < MAX_CONNECTION_ATTEMPTS) {
                            Log.d(TAG, "Will retry after error in ${CONNECTION_RETRY_DELAY}ms")
                            delay(CONNECTION_RETRY_DELAY)
                            continue
                        } else {
                            return@withContext false
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unexpected error during initialization: ${e.message}")
                    e.printStackTrace()

                    if (attempt < MAX_CONNECTION_ATTEMPTS) {
                        delay(CONNECTION_RETRY_DELAY)
                        continue
                    } else {
                        return@withContext false
                    }
                }
            }

            // If we get here, all attempts failed
            false
        }
    }

    /** Applies optimized settings to the ELM327 adapter for faster communication */
    private suspend fun optimizeAdapterSettings() {
        obd2Service?.let { service ->
            // Reset the adapter first to ensure clean state
//            service.sendCommand("ATZ")
//            delay(1000) // Wait for reset

//            // Set echo off - reduces unnecessary data
//            service.sendCommand("ATE0")
//            delay(200)

            // Set line feeds off - reduces unnecessary data
//            service.sendCommand("ATL0")
//            delay(200)

            // Keep headers ON - critical for both sensor readings and DTC scanning
//            service.sendCommand("ATH1")
//            delay(200)

            // Turn spaces off - reduces unnecessary data
//            service.sendCommand("ATS0")
//            delay(200)

            // Set the protocol to auto (helps with compatibility)
//            service.sendCommand("ATSP0")
//            delay(200)

            // Set timeout to a reasonable value (82ms × 4 = ~328ms)
            service.sendCommand("ATST64")
            delay(200)

            // Set adaptive timing to mode 2 (aggresive)
            service.sendCommand("ATAT2")
            delay(200)

//            // Confirm headers are ON (redundant but makes it explicit)
//            service.sendCommand("ATH1")
//            delay(200)

            // Send a command to verify everything is working
            service.sendCommand("0100")
            delay(300)
        }
    }

    /**
     * Listen for OBD2 messages directly from the adapter
     * @return A flow of OBD2Message objects
     */
    @SuppressLint("MissingPermission")
    fun listenForOBD2Messages(): Flow<OBD2Message> {
        // Check Bluetooth permission before starting flow
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            // If permission is missing, return empty flow with error message
            return flow {
                Log.e(TAG, "Missing BLUETOOTH_CONNECT permission")
                emit(
                    OBD2Message.createResponse(
                        response = "Error: Missing Bluetooth permission",
                        isError = true
                    )
                )
            }
                .flowOn(Dispatchers.IO)
        }

        return flow {
            if (!isInitialized || obd2Service == null) {
                Log.w(TAG, "Attempted to listen for messages before initialization")
                return@flow
            }

            try {
                Log.d(TAG, "Starting to listen for OBD2 responses")
                obd2Service?.listenForResponses()?.collect { response ->
                    val decodedValue =
                        if (response.unit.isNotEmpty()) {
                            "${response.decodedValue} ${response.unit}"
                        } else {
                            response.decodedValue
                        }

                    // Create an OBD2Message
                    val obd2Message =
                        OBD2Message.createResponse(
                            response = "${response.command}: ${decodedValue}",
                            isError = response.isError
                        )

                    emit(obd2Message)
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception while listening: ${e.message}")
                emit(
                    OBD2Message.createResponse(
                        response = "Security error: ${e.message}",
                        isError = true
                    )
                )
            }
        }
            .catch { e ->
                Log.e(TAG, "Error in message listening: ${e.message}")
                // Signal connection error by emitting an error message
                emit(
                    OBD2Message.createResponse(
                        response = "Connection error: ${e.message}",
                        isError = true
                    )
                )
            }
            .flowOn(Dispatchers.IO)
    }

    @SuppressLint("MissingPermission")
    suspend fun sendCommand(command: String): Boolean {
        // Check Bluetooth permission
        if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission")
            return false
        }

        return try {
            // Check if the service is initialized
            if (obd2Service == null) {
                Log.w(TAG, "Attempted to send command before service creation")
                return false
            }

            // Check if the OBD2 service is still connected
            if (!obd2Service!!.isConnected()) {
                Log.w(TAG, "OBD2 service disconnected, attempting to reconnect")

                // Try to reconnect socket if needed
                if (!socket.isConnected) {
                    try {
                        Log.d(TAG, "Reconnecting socket")
                        socket.connect()
                        delay(1000) // Give it time to stabilize
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to reconnect socket: ${e.message}")
                        return false
                    }
                }

                // Recreate streams and service
                try {
                    val inputStream = socket.inputStream
                    val outputStream = socket.outputStream

                    if (inputStream != null && outputStream != null) {
                        obd2Service = OBD2Service(inputStream, outputStream)

                        // Re-initialize the OBD2 connection
                        if (!initialize()) {
                            Log.e(TAG, "Failed to re-initialize OBD2 connection")
                            return false
                        }
                    } else {
                        Log.e(TAG, "Failed to get valid socket streams for reconnection")
                        return false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error recreating OBD2 service: ${e.message}")
                    return false
                }
            }

            if (!isInitialized) {
                Log.w(TAG, "Attempted to send command before initialization")
                return false
            }

            Log.d(TAG, "Sending OBD2 command: $command")
            val result = obd2Service?.sendCommand(command) ?: false

            if (!result) {
                Log.e(TAG, "Failed to send command: $command")
            }

            result
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception sending command: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        try {
            Log.d(TAG, "Closing OBD2Service and Bluetooth connection")
            obd2Service = null
            isInitialized = false

            // Check Bluetooth permission
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.e(TAG, "Missing BLUETOOTH_CONNECT permission, can't close socket properly")
                return
            }

            if (socket.isConnected) {
                socket.close()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception closing socket: ${e.message}")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing socket: ${e.message}")
        }
    }

    /**
     * Gets the last raw response received from the OBD2 adapter
     * @return The last raw response as a string, or an empty string if no response or service is
     * null
     */
    fun getLastRawResponse(): String {
        val response = obd2Service?.getLastRawResponse() ?: ""
        Log.d(TAG, "Returning last raw response: '$response'")
        return response
    }
}
