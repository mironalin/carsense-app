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
                            // Give it a moment to stabilize
                            delay(1000)
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

                    // Initialize the OBD2 adapter with extended timeout
                    try {
                        // Wait a bit before initializing
                        delay(1500)

                        val initResult = obd2Service?.initialize() ?: false
                        isInitialized = initResult

                        if (initResult) {
                            Log.d(TAG, "OBD2 initialization successful")
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
            if (!isInitialized || obd2Service == null) {
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
}
