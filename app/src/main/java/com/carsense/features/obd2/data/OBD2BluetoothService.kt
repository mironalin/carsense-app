package com.carsense.features.obd2.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.carsense.features.obd2.domain.OBD2Message
import com.carsense.features.obd2.domain.command.OBD2Command
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Manages Bluetooth communication with an OBD2 adapter.
 *
 * This class is responsible for:
 * - Establishing and maintaining a connection to a Bluetooth OBD2 adapter via a [BluetoothSocket].
 * - Initializing the underlying [OBD2Service] which handles raw command execution and adapter
 * setup.
 * - Sending AT (Attention) commands for adapter configuration and optimization.
 * - Executing OBD2 commands (both as [OBD2Command] objects and raw strings) to retrieve vehicle
 * data.
 * - Providing [Flow]s for receiving [OBD2Response] objects, allowing for asynchronous data streams.
 * - Handling connection retries, permission checks, and resource cleanup.
 *
 * @property socket The [BluetoothSocket] used for communication with the OBD2 adapter. This socket
 * must be established by the caller before passing it to this service.
 * @property context The Android [Context], used for permission checks (e.g.,
 * [Manifest.permission.BLUETOOTH_CONNECT]). Can be null, in which case permission checks are
 * bypassed (e.g., for use in unit tests or environments where context is not available or
 * permissions are handled externally).
 */
class OBD2BluetoothService(
    private val socket: BluetoothSocket,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "OBD2BluetoothService"
        private const val MAX_CONNECTION_ATTEMPTS = 3
        private const val CONNECTION_RETRY_DELAY = 1000L // Milliseconds
    }

    private var obd2Service: OBD2Service? = null
    private var isInitialized =
        false // Tracks if the service and adapter have been successfully initialized.

    /**
     * Checks if a specific Android permission has been granted.
     *
     * This is a utility function to encapsulate the permission check logic. If the provided
     * [context] is null, this method assumes permissions are granted or handled externally,
     * returning `true`.
     *
     * @param permission The Manifest permission string (e.g.,
     * [Manifest.permission.BLUETOOTH_CONNECT]) to check.
     * @return `true` if the permission is granted or if the context is null, `false` otherwise.
     */
    private fun hasPermission(permission: String): Boolean {
        return context?.let {
            ContextCompat.checkSelfPermission(it, permission) == PackageManager.PERMISSION_GRANTED
        }
            ?: true // If context is null, assume permission is granted
    }

    /**
     * Initializes the OBD2 communication channel and the adapter itself.
     *
     * This suspend function performs several critical steps:
     * 1. Checks for the [Manifest.permission.BLUETOOTH_CONNECT] permission if a [Context] is
     * available.
     * 2. Attempts to connect the [BluetoothSocket] if it's not already connected. This includes
     * retries.
     * 3. Retrieves the input and output streams from the socket.
     * 4. Creates an instance of [OBD2Service] using these streams.
     * 5. Initializes the adapter with optimized settings for better performance.
     *
     * The entire process (socket connection, stream retrieval, OBD2Service initialization) is
     * retried up to [MAX_CONNECTION_ATTEMPTS] times upon failure, with a delay of
     * [CONNECTION_RETRY_DELAY] between attempts.
     *
     * @return `true` if all initialization steps are completed. Returns `false` if any critical step fails after all retry attempts.
     */
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

                    // Initialize the OBD2 adapter with all optimized settings
                    try {
                        // Wait before initializing
                        delay(800)

                        // Let the service initialize the adapter with optimized settings
                        val initResult = obd2Service?.initializeWithOptimizedSettings() ?: false

                        if (initResult) {
                            Log.d(TAG, "OBD2 initialization with optimized settings successful")

                            // Give the adapter time to fully stabilize after initialization
                            Log.d(TAG, "Waiting for adapter to stabilize before proceeding...")
                            delay(2000)

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

    /**
     * Listens for OBD2 messages directly from the adapter and transforms them into [OBD2Message]
     * objects.
     *
     * This method provides a continuous [Flow] of messages. It first checks for the
     * [Manifest.permission.BLUETOOTH_CONNECT] permission. If not granted, it emits an error
     * [OBD2Message] and completes the flow.
     *
     * If initialized and permission is granted, it collects responses from
     * [OBD2Service.listenForResponses()], formats them (appending units if available), and wraps
     * them in [OBD2Message] objects.
     *
     * Errors during listening (e.g., [SecurityException], general [IOException] from the underlying
     * flow) are caught, logged, and emitted as error [OBD2Message] objects. The flow operates on
     * [Dispatchers.IO].
     *
     * @return A [Flow] emitting [OBD2Message] objects. The flow continues until the underlying
     * connection is closed or an unrecoverable error occurs.
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

    /**
     * Executes a given [OBD2Command] and returns a [Flow] of [OBD2Response] objects.
     *
     * This is the primary method for sending standard OBD2 PID requests. It ensures that the
     * [OBD2BluetoothService] is initialized before proceeding. If not initialized, it returns a
     * flow that emits a single error [OBD2Response].
     *
     * The actual command execution is delegated to [OBD2Service.executeCommand]. The flow operates
     * on [Dispatchers.IO] as command execution involves I/O operations.
     *
     * @param command The [OBD2Command] object encapsulating the raw command string and parsing
     * logic.
     * @return A [Flow] emitting one or more [OBD2Response] objects corresponding to the adapter's
     * reply. The flow completes after the full response is received (typically signaled by a prompt
     * character) or if a timeout or error occurs.
     * @throws IllegalStateException if called when [isInitialized] is false (this behavior might
     * change
     * ```
     *         based on the current implementation returning an error flow instead).
     * ```
     */
    fun executeOBD2Command(command: OBD2Command): Flow<OBD2Response> {
        if (!isInitialized || obd2Service == null) {
            Log.e(
                TAG,
                "OBD2BluetoothService not initialized, cannot send command: ${command.getCommand()}"
            )
            return flow {
                emit(OBD2Response.createError(command.getCommand(), "Service not initialized"))
            }
        }
        Log.d(TAG, "Forwarding command ${command.getCommand()} to OBD2Service.executeCommand")
        return obd2Service!!.executeCommand(
            command.getCommand()
        ) // OBD2Service.executeCommand handles IO context
    }

    /**
     * Executes a raw OBD2 command string (e.g., a PID like "010C" or a mode like "03") and returns
     * a [Flow] of [OBD2Response] objects.
     *
     * This overload is useful for sending commands that are not encapsulated by a specific
     * [OBD2Command] subclass, or for direct diagnostic/testing purposes. It checks for service
     * initialization similarly to the [OBD2Command] overload.
     *
     * The actual command execution is delegated to [OBD2Service.executeCommand]. The flow operates
     * on [Dispatchers.IO].
     *
     * @param command The raw OBD2 command string to be sent to the adapter.
     * @return A [Flow] emitting [OBD2Response] objects.
     * @throws IllegalStateException if called when [isInitialized] is false (similar to the other
     * overload).
     */
    fun executeOBD2Command(command: String): Flow<OBD2Response> {
        if (!isInitialized || obd2Service == null) {
            Log.e(TAG, "OBD2BluetoothService not initialized, cannot send command string: $command")
            return flow { emit(OBD2Response.createError(command, "Service not initialized")) }
        }
        Log.d(TAG, "Forwarding command string \"$command\" to OBD2Service.executeCommand")
        return obd2Service!!.executeCommand(command)
    }

    /**
     * Executes a raw AT (Attention) command and returns a [Flow] of [OBD2Response] objects.
     *
     * AT commands are used to configure, reset, or query the status of the ELM327 adapter itself
     * (e.g., "ATI" for adapter version, "ATZ" for reset). It checks for service initialization
     * similarly to the OBD2 command execution methods.
     *
     * The actual command execution is delegated to [OBD2Service.executeCommand], internally
     * indicating that it's an AT command for appropriate response handling by [OBD2Service]. The
     * flow operates on [Dispatchers.IO].
     *
     * @param atCommand The raw AT command string (e.g., "ATI", "ATE0").
     * @return A [Flow] emitting [OBD2Response] objects, typically containing the adapter's direct
     * textual reply.
     * @throws IllegalStateException if called when [isInitialized] is false.
     */
    fun executeAtCommand(atCommand: String): Flow<OBD2Response> {
        if (!isInitialized || obd2Service == null) {
            Log.e(TAG, "OBD2BluetoothService not initialized, cannot send AT command: $atCommand")
            return flow { emit(OBD2Response.createError(atCommand, "Service not initialized")) }
        }
        Log.d(TAG, "Forwarding AT command $atCommand to OBD2Service.executeCommand")
        return obd2Service!!.executeCommand(atCommand)
    }

    /**
     * Sends an [OBD2Command] and attempts to return a single [OBD2Message].
     *
     * @param command The [OBD2Command] to send.
     * @return Potentially an [OBD2Message] if the underlying deprecated mechanism in [OBD2Service]
     * could provide one; however, this is unreliable. Currently, it's hardcoded to return `null`
     * after logging a warning, as the response retrieval logic it relied upon has been removed.
     * @throws IllegalStateException if [obd2Service] is null (though this check might be bypassed
     * by current logic).
     *
     * @deprecated This method is deprecated. It relies on an outdated and unreliable mechanism for
     * response retrieval that has been removed from the underlying [OBD2Service]. Use
     * [executeOBD2Command] which returns a [Flow] for robust, asynchronous handling of potentially
     * multi-line responses and proper error/timeout management. For a single response, you might
     * use `executeOBD2Command(command).firstOrNull()`.
     */
    @Deprecated(
        "Use executeOBD2Command or executeAtCommand which provide specific response flows.",
        ReplaceWith("executeOBD2Command(commandObj)")
    )
    suspend fun sendOBD2Command(command: OBD2Command): OBD2Message? {
        Log.w(
            TAG,
            "sendOBD2Command(OBD2Command) is deprecated and will not function correctly for responses."
        )
        // This old method relies on a now-removed getLastRawResponse mechanism.
        // It can still send the command via the underlying service, but response retrieval is
        // broken.
        obd2Service?.sendCommand(
            command.getCommand()
        ) // This just sends, doesn't ensure response correlation
        return null // Cannot reliably return OBD2Message anymore.
    }

    /**
     * Attempts to get the last raw response string from the OBD2 adapter.
     *
     * @return Always `null`. This method is deprecated and its functionality has been removed.
     *
     * @deprecated This method is deprecated. Direct access to a "last raw response" is error-prone
     * and does not fit the asynchronous, flow-based communication model. Responses should be
     * consumed exclusively via the [Flow]s returned by [executeOBD2Command] or [executeAtCommand].
     * The underlying mechanism this method might have relied on no longer exists.
     */
    @Deprecated("Raw response access is error-prone and has been removed.")
    fun getLastRawResponse(): String? {
        Log.w(TAG, "getLastRawResponse() is deprecated and returns null.")
        return null // Was: obd2Service?.someOldMethodCall()
    }

    /**
     * Closes the Bluetooth socket and releases associated resources.
     *
     * This method should be called when the [OBD2BluetoothService] is no longer needed to prevent
     * resource leaks and ensure a clean shutdown of the Bluetooth connection. It performs the
     * following actions:
     * 1. Logs the closing attempt.
     * 2. Sets [obd2Service] to null and [isInitialized] to false.
     * 3. Checks for [Manifest.permission.BLUETOOTH_CONNECT] permission before attempting to close
     * the socket.
     * ```
     *    If permission is not granted and context is available, it logs an error and returns.
     * ```
     * 4. If the [socket] is connected and permission is granted (or context is null), it calls
     * [socket.close()].
     *
     * Catches and logs [SecurityException] or [IOException] that may occur during socket closure.
     */
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
     * Checks if the service has been successfully initialized and the underlying [OBD2Service]
     * instance exists.
     *
     * This method can be used to quickly verify if the [OBD2BluetoothService] is likely ready to
     * accept commands before attempting to call methods like [executeOBD2Command] or
     * [executeAtCommand].
     *
     * @return `true` if [isInitialized] is true and [obd2Service] is not null, `false` otherwise.
     */
    fun isReady(): Boolean {
        return isInitialized && obd2Service != null
    }
}
