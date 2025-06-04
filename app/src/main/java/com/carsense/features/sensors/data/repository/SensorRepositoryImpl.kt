package com.carsense.features.sensors.data.repository

import android.util.Log
import com.carsense.features.bluetooth.domain.BluetoothController
import com.carsense.features.sensors.data.service.SensorSnapshotCollector
import com.carsense.features.sensors.domain.command.CoolantTemperatureCommand
import com.carsense.features.sensors.domain.command.EngineLoadCommand
import com.carsense.features.sensors.domain.command.FuelLevelCommand
import com.carsense.features.sensors.domain.command.IntakeAirTemperatureCommand
import com.carsense.features.sensors.domain.command.IntakeManifoldPressureCommand
import com.carsense.features.sensors.domain.command.MassAirFlowCommand
import com.carsense.features.sensors.domain.command.RPMCommand
import com.carsense.features.sensors.domain.command.SensorCommand
import com.carsense.features.sensors.domain.command.SpeedCommand
import com.carsense.features.sensors.domain.command.SupportedPIDsCommand
import com.carsense.features.sensors.domain.command.ThrottlePositionCommand
import com.carsense.features.sensors.domain.command.TimingAdvanceCommand
import com.carsense.features.sensors.domain.model.SensorCategories
import com.carsense.features.sensors.domain.model.SensorCategory
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.domain.repository.SensorRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Implementation of [SensorRepository] responsible for fetching and managing vehicle sensor data
 * via an OBD2 adapter.
 *
 * This class handles:
 * - Registration of available [SensorCommand]s.
 * - Detection of PIDs (Parameter IDs) supported by the connected vehicle.
 * - Execution of individual and potentially batched OBD2 commands to retrieve sensor data.
 * - Caching of the latest [SensorReading]s.
 * - A continuous monitoring loop to periodically poll selected sensors and emit updates via a
 * [Flow].
 * - Serialization of OBD2 command execution using a [Mutex] to prevent concurrent access to the
 * Bluetooth OBD2 service.
 * - Interaction with the [BluetoothController] to get access to the underlying
 * [OBD2BluetoothService].
 * - Adaptive timing for sensor polling based on observed query times.
 *
 * It aims to provide a robust and efficient way to access a wide range of vehicle diagnostics.
 *
 * @param bluetoothController The [BluetoothController] used to access the underlying
 * [OBD2BluetoothService] for sending commands and receiving responses.
 */
@Singleton
class SensorRepositoryImpl
@Inject
constructor(
    private val bluetoothController: BluetoothController,
    private val sensorSnapshotCollector: SensorSnapshotCollector
) : SensorRepository {

    // Tag for logging
    private val TAG = "SensorRepository"

    // Mutex to ensure only one OBD2 command is active at a time across the repository.
    // This is critical because the ELM327 adapter and the underlying OBD2 communication
    // channel are typically single-threaded and cannot handle concurrent commands gracefully.
    // All OBD2 command executions (e.g., readSensor, detectSupportedPIDs) must acquire this lock.
    private val obd2CommunicationMutex = Mutex()

    // Registry of all available sensor commands
    private val allSensorCommands = mutableMapOf<String, SensorCommand>()

    // Registry of supported sensor commands (confirmed by the vehicle)
    private val supportedSensorCommands = mutableMapOf<String, SensorCommand>()

    // Cache of the latest readings
    private val latestReadings = mutableMapOf<String, SensorReading>()

    // Cache of command strings to avoid rebuilding them
    private val commandStringCache = mutableMapOf<String, String>()

    // Shared flow of all sensor readings
    private val _sensorReadings = MutableSharedFlow<SensorReading>(replay = 0)

    // Flag to track if PID support detection has been run
    private var supportDetectionRun = false

    // Flag to track if monitoring is active
    private var isMonitoring = false

    // Flag to indicate whether batch mode is supported
    private var isBatchModeSupported = false

    // Flag to track if we've attempted to detect batch mode
    private var batchModeDetectionRun = false

    // Coroutine job for the monitoring loop
    private var monitoringJob: Job? = null

    // Supervisor job for sensor reading operations
    private val supervisorJob = SupervisorJob()

    // Coroutine scope that uses the supervisor job to prevent error propagation
    private val sensorScope = CoroutineScope(Dispatchers.IO + supervisorJob)

    // Maximum number of retries for connection checks
    private val MAX_CONNECTION_RETRIES = 2

    // Maximum number of sensors to process per high priority cycle
    private val MAX_HIGH_PRIORITY_SENSORS_PER_CYCLE = 2

    // Maximum number of sensors to process per medium/low priority cycle
    private val MAX_LOWER_PRIORITY_SENSORS_PER_CYCLE = 1

    // Minimum time to wait between sensor readings to avoid overloading the adapter
    private val MIN_SENSOR_DELAY_MS = 1L

    // Default connection check timeout
    private val CONNECTION_CHECK_TIMEOUT_MS = 250L

    // Maximum time to wait for a response from the adapter
    private val COMMAND_TIMEOUT_MS = 1500L

    // Average time per sensor query based on observations (used for adaptive timing)
    private var averageSensorQueryTimeMs = 120L

    // Number of recent sensor timings to keep for average calculation
    private val MAX_TIMINGS = 10

    // Recent timings for sensor queries to calculate moving average
    private val recentQueryTimings = mutableListOf<Long>()

    /**
     * Initializes the sensor repository by registering all available sensor commands.
     *
     * This constructor is called when the [SensorRepositoryImpl] is created. It registers all
     * available [SensorCommand]s to ensure that the repository can handle all possible sensor
     * requests.
     */
    init {
        // Register all sensor commands
        registerAllSensorCommands()
    }

    /**
     * Registers all available sensor commands.
     *
     * This method is called during initialization to ensure that the repository can handle all
     * possible sensor requests. It maps each [SensorCommand] to its corresponding PID and stores
     * them in the [allSensorCommands] map.
     */
    private fun registerAllSensorCommands() {
        val commands =
            listOf(
                RPMCommand(),
                SpeedCommand(),
                CoolantTemperatureCommand(),
                FuelLevelCommand(),
                EngineLoadCommand(),
                IntakeAirTemperatureCommand(),
                ThrottlePositionCommand(),
                IntakeManifoldPressureCommand(),
                TimingAdvanceCommand(),
                MassAirFlowCommand(),
                SupportedPIDsCommand()
            )

        commands.forEach { command ->
            allSensorCommands[command.pid] = command

            // Pre-cache command strings for faster access later
            commandStringCache[command.pid] =
                if (!command.getCommand().startsWith("01") && command.pid != "00") {
                    "01${command.pid}"
                } else {
                    command.getCommand()
                }
        }

        // Initially, populate the supported commands with all commands
        // They will be filtered after running PID support detection
        supportedSensorCommands.putAll(allSensorCommands)

        Log.d(TAG, "Registered ${allSensorCommands.size} sensor commands")
    }

    /**
     * Detects which PIDs (Parameter IDs) are supported by the connected vehicle.
     *
     * This function queries the vehicle for standard PID support (typically commands "00", "20",
     * "40", etc., for Mode 01). It uses the [SupportedPIDsCommand] to achieve this. The process
     * involves:
     * 1. Checking if already connected via [BluetoothController].
     * 2. Obtaining the [OBD2BluetoothService] from the [bluetoothController].
     * 3. Executing the [SupportedPIDsCommand] (e.g., "0100") using
     * `obd2Service.executeOBD2Command()`.
     * 4. Collecting and parsing the [OBD2Response]. The response is a bitmask indicating supported
     * PIDs in ranges.
     * 5. Updating the [supportedSensorCommands] map based on the parsed response.
     * 6. If the primary PID support detection fails (e.g., timeout, error response), it may fall
     * back to [useHardcodedPIDSupport] or attempt querying subsequent PID support ranges (e.g.,
     * "0120", "0140"). This operation is protected by the [obd2CommunicationMutex].
     *
     * @return `true` if PID support was successfully detected and processed (even if some ranges
     * failed), `false` if a critical error occurred preventing any PID support assessment or if not
     * connected.
     */
    private suspend fun detectSupportedPIDs(): Boolean {
        if (!bluetoothController.isConnected.value) {
            Log.e(TAG, "Not connected to vehicle, cannot detect supported PIDs")
            return false
        }

        val obd2Service = bluetoothController.getObd2Service()
        if (obd2Service == null) {
            Log.e(TAG, "OBD2Service not available, cannot detect supported PIDs")
            return false
        }

        var localReading: SensorReading? = null
        var localErrorResult = false
        var localErrorMessage = ""

        try {
            Log.d(TAG, "Detecting supported PIDs...")

            val supportedPIDsCommand =
                allSensorCommands["00"] as? SupportedPIDsCommand
                    ?: run {
                        Log.e(
                            TAG,
                            "SupportedPIDsCommand (00) not found in allSensorCommands registry."
                        )
                        return false // Should not happen if registered in init
                    }

            // Execute the command and collect the first (and typically only) response
            // Adding a timeout for this specific operation
            withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                obd2Service.executeOBD2Command(supportedPIDsCommand)
                    .collect { obd2Response: com.carsense.features.obd2.data.OBD2Response
                        -> // Explicit type
                        if (obd2Response.isError) {
                            Log.e(
                                TAG,
                                "Error response from supported PIDs command: ${obd2Response.decodedValue}"
                            )
                            localErrorResult = true
                            localErrorMessage = obd2Response.decodedValue
                            throw CancellationException("Error received for PID support detection")
                        } else {
                            localReading =
                                SensorReading(
                                    name = supportedPIDsCommand.displayName,
                                    value = obd2Response.decodedValue,
                                    unit = supportedPIDsCommand.unit,
                                    pid = supportedPIDsCommand.pid,
                                    mode = supportedPIDsCommand.mode,
                                    rawValue = obd2Response.rawData,
                                    isError = false,
                                    timestamp = obd2Response.timestamp // Added timestamp
                                )
                            Log.d(
                                TAG,
                                "Successfully received and decoded PID support: ${obd2Response.decodedValue}"
                            )
                            throw CancellationException("Successfully collected PID support data")
                        }
                    }
            }
                ?: run {
                    Log.w(TAG, "Timeout detecting supported PIDs after ${COMMAND_TIMEOUT_MS}ms")
                    if (!localErrorResult) {
                        localErrorMessage = "Timeout waiting for PID support response"
                        localErrorResult = true
                    }
                }

            // This part is reached if timeout occurred OR flow completed without cancellation
            // (which shouldn't happen if data/error was processed correctly with cancellation)
            if (localErrorResult) {
                Log.e(
                    TAG,
                    "Failed to detect supported PIDs due to error/timeout: $localErrorMessage"
                )
                return useHardcodedPIDSupport()
            }

            if (localReading == null) {
                // This case implies timeout without error, or flow completed unexpectedly
                Log.e(
                    TAG,
                    "No valid reading obtained from supported PIDs command (timeout or unexpected flow completion)."
                )
                return useHardcodedPIDSupport()
            }

            // If we reach here, it means localReading is set, and no error flagged from within
            // collect/timeout
            // This path should ideally not be taken if CancellationException logic works as
            // intended.
            // For safety, process localReading if it somehow exists here.
            Log.w(TAG, "Processing PID support data outside of cancellation path, check logic.")
            val supportedPIDs =
                localReading!!.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            Log.d(TAG, "Detected supported PIDs (fallback path): $supportedPIDs")

            if (supportedPIDs.isEmpty()) {
                Log.w(
                    TAG,
                    "No PIDs reported as supported (fallback path), using hardcoded fallbacks"
                )
                return useHardcodedPIDSupport()
            }

            val finalSupportedPIDsCommand =
                allSensorCommands["00"] as? SupportedPIDsCommand
                    ?: return useHardcodedPIDSupport()
            supportedSensorCommands.clear()
            for (pid in supportedPIDs) {
                allSensorCommands[pid]?.let { command -> supportedSensorCommands[pid] = command }
            }
            supportedSensorCommands["00"] = finalSupportedPIDsCommand

            Log.d(
                TAG,
                "Updated supported sensor commands (fallback path): ${supportedSensorCommands.keys}"
            )
            supportDetectionRun = true
            return true
        } catch (e: CancellationException) {
            // This is the expected path for successful collection or handled error
            if (localReading != null && !localErrorResult) {
                Log.d(TAG, "PID support detection successful (flow cancelled post-collection).")
                val supportedPIDs =
                    localReading!!.value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                Log.d(TAG, "Detected supported PIDs (post-cancellation): $supportedPIDs")

                if (supportedPIDs.isEmpty()) {
                    Log.w(
                        TAG,
                        "No PIDs reported as supported (post-cancellation), using hardcoded fallbacks"
                    )
                    return useHardcodedPIDSupport()
                }
                val supportedPIDsCommand =
                    allSensorCommands["00"] as? SupportedPIDsCommand
                        ?: return useHardcodedPIDSupport()
                supportedSensorCommands.clear()
                for (pid in supportedPIDs) {
                    allSensorCommands[pid]?.let { command ->
                        supportedSensorCommands[pid] = command
                    }
                }
                supportedSensorCommands["00"] = supportedPIDsCommand
                Log.d(
                    TAG,
                    "Updated supported sensor commands (post-cancellation): ${supportedSensorCommands.keys}"
                )
                supportDetectionRun = true
                return true
            } else if (localErrorResult) {
                Log.e(
                    TAG,
                    "PID support detection failed (flow cancelled due to error): $localErrorMessage"
                )
                return useHardcodedPIDSupport()
            }
            Log.w(TAG, "PID support detection flow cancelled without success or specific error.", e)
            return useHardcodedPIDSupport() // Treat other cancellations as failure for this op
        } catch (e: Exception) {
            Log.e(TAG, "Generic exception during PID support detection: ${e.message}", e)
            return useHardcodedPIDSupport()
        }
    }

    /**
     * Fallback method to use hardcoded PIDs that are commonly supported by most vehicles
     *
     * This method is used as a fallback when the primary PID support detection fails. It provides a
     * list of common PIDs that are typically supported by most vehicles. It updates the
     * [supportedSensorCommands] map with these PIDs and returns `true` to indicate that the
     * fallback was used.
     *
     * @return True to indicate the fallback was used
     */
    private fun useHardcodedPIDSupport(): Boolean {
        Log.d(TAG, "Using hardcoded PID support fallback")

        // Common PIDs supported by most vehicles
        val commonPIDs =
            listOf(
                "04", // Engine Load
                "05", // Coolant Temperature
                "0C", // RPM
                "0D", // Speed
                "0F", // Intake Temperature
                "10", // MAF
                "11", // Throttle Position
                "2F" // Fuel Level
            )

        // Update the supported commands map
        supportedSensorCommands.clear()
        for (pid in commonPIDs) {
            allSensorCommands[pid]?.let { command -> supportedSensorCommands[pid] = command }
        }

        // Always include the supported PIDs command
        allSensorCommands["00"]?.let { command -> supportedSensorCommands["00"] = command }

        Log.d(TAG, "Set hardcoded supported commands: ${supportedSensorCommands.keys}")
        supportDetectionRun = true
        return true
    }

    /**
     * Provides a [Flow] of [SensorReading] objects.
     *
     * This flow emits sensor data as it becomes available from the monitoring loop (started by
     * [startMonitoring]) or from individual calls to [readSensor] if they also choose to emit to
     * the shared flow.
     *
     * Consumers can collect from this flow to receive real-time updates of sensor values. It is a
     * [MutableSharedFlow] internally, exposed as a [SharedFlow], meaning it does not hold a state
     * for late subscribers by default (replay = 0) but will share emissions among active
     * collectors.
     *
     * @return A [SharedFlow] that emits [SensorReading] objects.
     */
    override fun getSensorReadings(sensorId: String): Flow<SensorReading> {
        return _sensorReadings.asSharedFlow().filter { it.pid == sensorId }
    }

    /**
     * Returns the latest reading for a specific sensor ID.
     *
     * This method is used to get the latest reading for a specific sensor ID. It is used to get the
     * latest reading for a specific sensor.
     */
    override suspend fun getLatestReading(sensorId: String): SensorReading? {
        return latestReadings[sensorId]
    }

    /**
     * Requests a reading for a specific sensor ID.
     *
     * This method is used to request a reading for a specific sensor ID. It is used to request a
     * reading for a specific sensor.
     */
    override suspend fun requestReading(sensorId: String): Result<SensorReading> {
        val startTime = System.currentTimeMillis()

        if (!supportDetectionRun) {
            if (!detectSupportedPIDs()) {
                Log.w(TAG, "PID support detection failed; cannot request reading for $sensorId.")
                return Result.failure(IllegalStateException("PID support detection failed."))
            }
        }

        val command =
            supportedSensorCommands[sensorId]
                ?: allSensorCommands[sensorId]
                ?: return Result.failure(
                    IllegalArgumentException(
                        "Sensor command not found for ID: $sensorId"
                    )
                )

        val obd2Service = bluetoothController.getObd2Service()
        if (obd2Service == null) {
            Log.e(TAG, "OBD2Service not available for sensor $sensorId")
            return Result.failure(IllegalStateException("OBD2Service not available"))
        }

        var operationErrorResult = false
        var operationErrorMessage = "Request failed"
        var operationFinalReading: SensorReading? = null

        obd2CommunicationMutex.withLock {
            try {
                Log.d(
                    TAG,
                    "Requesting reading for $sensorId using command: ${command.getCommand()}"
                )

                var attempt = 0
                val maxAttempts = 2

                while (attempt < maxAttempts &&
                    operationFinalReading == null &&
                    !operationErrorResult
                ) {
                    attempt++
                    if (attempt > 1) {
                        Log.d(TAG, "Retrying command for $sensorId, attempt $attempt")
                        delay(200)
                    }

                    var currentAttemptError = false
                    var currentAttemptErrorMessage = ""

                    withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                        obd2Service.executeOBD2Command(command)
                            .collect { obd2Response: com.carsense.features.obd2.data.OBD2Response ->
                                if (obd2Response.isError) {
                                    currentAttemptErrorMessage = obd2Response.decodedValue
                                    Log.w(
                                        TAG,
                                        "Error response for $sensorId (attempt $attempt): $currentAttemptErrorMessage"
                                    )
                                    if (currentAttemptErrorMessage.contains(
                                            "SEARCHING",
                                            ignoreCase = true
                                        ) ||
                                        currentAttemptErrorMessage.contains(
                                            "BUSINIT",
                                            ignoreCase = true
                                        ) && attempt < maxAttempts
                                    ) {
                                        // Log and allow retry loop to continue
                                        Log.d(
                                            TAG,
                                            "Adapter possibly initializing for $sensorId, will retry."
                                        )
                                        currentAttemptError =
                                            true // Mark this attempt as having an error, but
                                        // retryable
                                    } else {
                                        operationErrorResult =
                                            true // Non-retryable error for the whole operation
                                        operationErrorMessage = currentAttemptErrorMessage
                                    }
                                    throw CancellationException(
                                        "Error or retryable condition for $sensorId in attempt $attempt"
                                    )
                                } else {
                                    val currentVal = obd2Response.decodedValue
                                    if (currentVal == "FFFFFFFF" ||
                                        currentVal == "-1" ||
                                        currentVal.isBlank()
                                    ) {
                                        currentAttemptErrorMessage =
                                            "Invalid data value: $currentVal"
                                        Log.w(
                                            TAG,
                                            "Invalid data for $sensorId: $currentVal (attempt $attempt)"
                                        )
                                        operationErrorResult =
                                            true // Treat as a non-retryable error for the whole
                                        // operation
                                        operationErrorMessage = currentAttemptErrorMessage
                                        throw CancellationException(
                                            "Invalid data value for $sensorId in attempt $attempt"
                                        )
                                    }
                                    operationFinalReading =
                                        SensorReading(
                                            name = command.displayName,
                                            value = currentVal,
                                            unit = command.unit,
                                            pid = command.pid,
                                            mode = command.mode,
                                            rawValue = obd2Response.rawData,
                                            isError = false,
                                            timestamp = obd2Response.timestamp
                                        )
                                    Log.d(
                                        TAG,
                                        "Success for $sensorId (attempt $attempt): $currentVal ${command.unit}"
                                    )
                                    throw CancellationException(
                                        "Successfully collected reading for $sensorId in attempt $attempt"
                                    )
                                }
                            }
                    }
                        ?: run {
                            Log.w(
                                TAG,
                                "Timeout for $sensorId (attempt $attempt) after ${COMMAND_TIMEOUT_MS}ms"
                            )
                            if (!operationErrorResult
                            ) { // Don't overwrite a more specific error
                                currentAttemptErrorMessage = "Timeout for attempt $attempt"
                                // If timeout on last attempt, it becomes an operation error
                                if (attempt == maxAttempts) {
                                    operationErrorResult = true
                                    operationErrorMessage = currentAttemptErrorMessage
                                } else {
                                    // Allow retry if not max attempts
                                    currentAttemptError = true
                                }
                            }
                        }
                    if (operationFinalReading != null) break
                    if (operationErrorResult)
                        break // Break if a non-retryable error for the whole op occurred
                    // If currentAttemptError is true but operationErrorResult is false, it was a
                    // retryable error, so loop continues
                }
            } catch (e: CancellationException) {
                // Expected for breaking .collect{} or if withTimeoutOrNull is cancelled by an outer
                // scope.
                // The state of operationFinalReading and operationErrorResult determines the
                // outcome.
                Log.d(TAG, "Cancellation in requestReading for $sensorId: ${e.message}")
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Generic exception during OBD2 communication for $sensorId: ${e.message}",
                    e
                )
                operationErrorResult = true
                operationErrorMessage = "Network/IO Error: ${e.message}"
            }
        } // End of Mutex lock

        return if (operationFinalReading != null) {
            latestReadings[sensorId] = operationFinalReading!!
            _sensorReadings.emit(operationFinalReading!!)

            // Add the reading to the snapshot collector
            val snapshotCompleted = sensorSnapshotCollector.addReading(operationFinalReading!!)
            if (snapshotCompleted) {
                Log.d(TAG, "Completed a full circle snapshot with sensor $sensorId")
            }

            val queryTime = System.currentTimeMillis() - startTime
            updateAverageQueryTime(queryTime)
            Log.i(TAG, "Successfully read $sensorId: ${operationFinalReading!!.value}")
            Result.success(operationFinalReading!!)
        } else {
            Log.e(TAG, "All attempts failed for $sensorId. Last error: $operationErrorMessage")
            Result.failure(
                IllegalStateException(
                    "Failed to get reading for $sensorId: $operationErrorMessage"
                )
            )
        }
    }

    /**
     * Returns a list of all available sensors.
     *
     * This method is used to get a list of all available sensors. It is used to get the list of all
     * available sensors.
     */
    override suspend fun getAvailableSensors(): List<String> {
        // Ensure we've run PID support detection
        if (!supportDetectionRun) {
            detectSupportedPIDs()
        }

        // Return supported sensors first, then fall back to all sensors if none are supported
        return if (supportedSensorCommands.isNotEmpty()) {
            supportedSensorCommands.keys.toList()
        } else {
            allSensorCommands.keys.toList()
        }
    }

    /**
     * Retrieves a list of sensor commands that are confirmed to be supported by the vehicle.
     *
     * This method relies on the [supportedSensorCommands] map, which is populated by
     * [detectSupportedPIDs]. If PID support detection has not run, it might return all registered
     * commands or an empty list, depending on initialization logic.
     *
     * Optionally, the list can be filtered by a [SensorCategory].
     *
     * @param category An optional [SensorCategory] to filter the results. If `null`, all supported
     * sensor commands are returned.
     * @return A list of [SensorCommand] objects that the connected vehicle supports. Returns an
     * empty list if PID detection hasn't run successfully or no sensors are supported/match the
     * category.
     */
    override suspend fun getSupportedSensors(): List<String> {
        // Ensure we've run PID support detection
        if (!supportDetectionRun) {
            detectSupportedPIDs()
        }

        return supportedSensorCommands.keys.toList()
    }

    /**
     * Returns a list of all sensor categories.
     *
     * This method is used to get a list of all sensor categories. It is used to get the list of all
     * sensor categories.
     */
    override suspend fun getSensorCategories(): List<SensorCategory> {
        return SensorCategories.getAllCategories()
    }

    /**
     * Returns a list of all sensors in a specific category.
     *
     * This method is used to get a list of all sensors in a specific category. It is used to get
     * the list of all sensors in a specific category.
     */
    override suspend fun getSensorsInCategory(categoryId: String): List<String> {
        val category = SensorCategories.getCategoryById(categoryId) ?: return emptyList()
        return category.sensorPids
    }

    /**
     * Detects which sensors are supported by the connected vehicle.
     *
     * This method is used to detect which sensors are supported by the connected vehicle. It is
     * used to detect which sensors are supported by the connected vehicle.
     */
    override suspend fun detectSupportedSensors(forceRefresh: Boolean): Boolean {
        if (forceRefresh || !supportDetectionRun) {
            return detectSupportedPIDs()
        }
        return supportDetectionRun
    }

    /**
     * Starts a continuous monitoring loop to read selected sensor PIDs at a specified interval.
     *
     * This function launches a new coroutine that:
     * 1. Initializes PID support by calling [detectSupportedPIDs] if not already done.
     * 2. Filters the `selectedPIDs` against the [supportedSensorCommands].
     * 3. Enters a loop that continues as long as [isMonitoring] is true and the coroutine is
     * active.
     * 4. Inside the loop, it iterates through the supported selected PIDs.
     * 5. For each PID, it calls [readSensor] to get the latest [SensorReading].
     * 6. Successfully read sensor data is emitted to the [_sensorReadings] shared flow.
     * 7. Implements adaptive timing: dynamically adjusts delays between sensor reads based on
     * ```
     *    [averageSensorQueryTimeMs] and the overall `updateIntervalMs` to try and meet the
     *    target update rate without overloading the OBD2 bus.
     * ```
     * 8. Prioritizes sensors based on their category (e.g., high-priority sensors like RPM, Speed
     * ```
     *    might be polled more frequently or with less delay within a cycle).
     * ```
     * 9. Handles potential errors during sensor reads, logging them but generally continuing the
     * loop.
     * 10. Ensures a minimum delay ([MIN_SENSOR_DELAY_MS]) between individual sensor requests.
     *
     * If already monitoring, it stops the current job and starts a new one with the new parameters.
     * The monitoring occurs on [Dispatchers.IO].
     *
     * @param selectedPIDs A list of PID strings (e.g., "0C" for RPM) to monitor.
     * @param updateIntervalMs The target interval in milliseconds at which a full cycle of polling
     * the selected PIDs should ideally complete. Adaptive timing will attempt to honor this.
     */
    override suspend fun startMonitoringSensors(sensorIds: List<String>, updateIntervalMs: Long) {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already active, stopping current monitoring")
            stopMonitoring()
        }

        isMonitoring = true

        // Begin with a detection of supported PIDs
        try {
            if (!supportDetectionRun) {
                detectSupportedPIDs()
            }
        } catch (e: Exception) {
            Log.w(TAG, "PID detection failed during monitoring start, continuing anyway", e)
        }

        // Send a sequence of commands to "prime" the adapter
        try {
            primeAdapter()
        } catch (e: Exception) {
            Log.w(TAG, "Adapter priming failed, continuing anyway", e)
        }

        // Disable batch mode (adapter doesn't support it properly yet)
        val useBatchMode = false

        // Start the monitoring coroutine
        monitoringJob =
            CoroutineScope(Dispatchers.IO).launch {
                Log.d(TAG, "Starting monitoring for specific sensors: $sensorIds")

                // For individual mode, keep a small delay to ensure proper handling
                val betweenSensorDelayMs = 1L

                // Use a smaller buffer for calculations
                val safeIntervalMs = updateIntervalMs * 0.9

                Log.d(
                    TAG,
                    "Update interval: ${updateIntervalMs}ms, between sensors delay: ${betweenSensorDelayMs}ms, batch mode: $useBatchMode"
                )

                while (isActive && isMonitoring) {
                    val cycleStartTime = System.currentTimeMillis()

                    if (!bluetoothController.isConnected.value) {
                        Log.d(TAG, "Not connected, skipping sensor updates")
                        delay(500) // Reduced wait time before checking connection again
                        continue
                    }

                    // Process sensors one by one (more reliable)
                    for (sensorId in sensorIds) {
                        try {
                            if (allSensorCommands.containsKey(sensorId)) {
                                requestReading(sensorId)
                                if (sensorIds.size > 1) {
                                    delay(betweenSensorDelayMs)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error monitoring sensor $sensorId", e)
                        }
                    }

                    // Calculate remaining time in this update cycle
                    val cycleDuration = System.currentTimeMillis() - cycleStartTime
                    val remainingTime = updateIntervalMs - cycleDuration

                    // Wait for the remaining time until next update cycle, with reduced minimum
                    // wait
                    if (remainingTime > 50) {
                        delay(remainingTime)
                    } else {
                        Log.w(
                            TAG,
                            "Sensor polling cycle took longer than update interval (${cycleDuration}ms > ${updateIntervalMs}ms)"
                        )
                        // delay(50) // Use 50ms as minimum delay
                    }
                }

                Log.d(TAG, "Sensor monitoring stopped")
            }
    }

    /**
     * Starts monitoring all sensors.
     *
     * This method is used to start monitoring all sensors. It is used to start monitoring all
     * sensors.
     */
    override suspend fun startMonitoring(updateIntervalMs: Long) {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already active")
            return
        }

        isMonitoring = true

        // Ensure we've run PID support detection
        if (!supportDetectionRun) {
            detectSupportedPIDs()
        }

        // Get the list of sensors to monitor (excluding PID support command)
        val sensorsToMonitor = supportedSensorCommands.keys.filter { it != "00" }

        // Start monitoring using the optimized method
        startMonitoringSensors(sensorsToMonitor, updateIntervalMs)
    }

    /**
     * Stops monitoring all sensors.
     *
     * This method is used to stop monitoring all sensors. It is used to stop monitoring all
     * sensors.
     */
    override suspend fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        isMonitoring = false

        try {
            // Cancel the monitoring job and wait for it to complete
            monitoringJob?.cancel()
            monitoringJob = null

            // Small delay to ensure cancellation propagates
            delay(50)

            // Cancel any children jobs of the supervisor job without cancelling the supervisor
            // itself
            supervisorJob.children.forEach { it.cancel() }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping monitoring", e)
        }

        Log.d(TAG, "Stopped sensor monitoring")
    }

    /**
     * Checks connection with retries.
     *
     * This method is used to check connection with retries. It is used to check connection with
     * retries.
     *
     * @return true if connected, false otherwise
     */
    private suspend fun checkConnectionWithRetry(): Boolean {
        var retries = 0

        // First fast check
        if (bluetoothController.isConnected.value) {
            return true
        }

        // Try up to MAX_CONNECTION_RETRIES times
        while (retries < MAX_CONNECTION_RETRIES) {
            Log.d(TAG, "Connection check retry ${retries + 1}/${MAX_CONNECTION_RETRIES}")

            if (bluetoothController.isConnected.value) {
                return true
            }

            retries++
            delay(500) // Wait before retrying
        }

        return false
    }

    /**
     * Primes the OBD adapter by sending a simple command (like RPM) a few times. This can help wake
     * up the adapter or ensure it's responsive before intensive polling.
     */
    private suspend fun primeAdapter() {
        Log.d(TAG, "Priming OBD2 adapter...")
        val obd2Service = bluetoothController.getObd2Service()
        if (obd2Service == null) {
            Log.w(TAG, "OBD2Service not available, skipping adapter priming.")
            return
        }

        // RPM PID is "0C"
        val rpmCommand = allSensorCommands["0C"] as? RPMCommand
        if (rpmCommand == null) {
            Log.w(TAG, "RPMCommand (PID 0C) not found, cannot prime adapter.")
            return
        }

        var successful = false
        val maxAttempts = 3

        obd2CommunicationMutex.withLock {
            for (attempt in 1..maxAttempts) {
                if (!bluetoothController.isConnected.value) {
                    Log.w(TAG, "Not connected, aborting prime attempt $attempt")
                    break
                }
                try {
                    Log.d(TAG, "Priming attempt $attempt with ${rpmCommand.getCommand()}")
                    var responseReceived = false
                    withTimeoutOrNull(1000L) { // Shorter timeout for priming
                        obd2Service.executeOBD2Command(rpmCommand)
                            .collect { obd2Response: com.carsense.features.obd2.data.OBD2Response ->
                                responseReceived = true
                                if (obd2Response.isError) {
                                    Log.w(
                                        TAG,
                                        "Priming attempt $attempt error: ${obd2Response.decodedValue}"
                                    )
                                    if (obd2Response.decodedValue.contains(
                                            "SEARCHING",
                                            ignoreCase = true
                                        ) ||
                                        obd2Response.decodedValue.contains(
                                            "BUSINIT",
                                            ignoreCase = true
                                        )
                                    ) {
                                        // Allow retry
                                    } else {
                                        // For other errors, might stop trying earlier, but for priming,
                                        // retrying is generally safe.
                                    }
                                } else {
                                    Log.d(
                                        TAG,
                                        "Priming attempt $attempt success: ${obd2Response.decodedValue}"
                                    )
                                    successful = true
                                }
                                throw CancellationException(
                                    "Collected response for priming attempt $attempt"
                                ) // Stop collecting for this attempt
                            }
                    }
                        ?: run { Log.w(TAG, "Priming attempt $attempt timed out.") }

                    if (successful) break // Exit loop if priming was successful
                    if (attempt < maxAttempts)
                        delay((200 * attempt).toLong()) // Delay before next attempt
                } catch (e: CancellationException) {
                    Log.d(TAG, "Cancellation during priming attempt $attempt: ${e.message}")
                    if (successful) break
                } catch (e: Exception) {
                    Log.e(TAG, "Exception during priming attempt $attempt: ${e.message}")
                    if (attempt < maxAttempts) delay((200 * attempt).toLong()) else break
                }
            }
        }

        if (successful) {
            Log.d(TAG, "Adapter priming completed successfully.")
        } else {
            Log.w(
                TAG,
                "Adapter priming may not have been fully successful after $maxAttempts attempts."
            )
        }
    }

    /**
     * Attempts to detect if the adapter supports batch mode.
     *
     * This method is used to detect if the adapter supports batch mode. It is used to detect if the
     * adapter supports batch mode.
     *
     * @return True if batch mode is detected as supported
     */
    private suspend fun detectBatchModeSupport(): Boolean {
        if (batchModeDetectionRun) {
            return isBatchModeSupported
        }

        val obd2Service = bluetoothController.getObd2Service()
        if (obd2Service == null) {
            Log.w(TAG, "OBD2Service not available, skipping batch mode detection.")
            batchModeDetectionRun = true
            isBatchModeSupported = false
            return false
        }

        obd2CommunicationMutex.withLock {
            try {
                Log.d(TAG, "Detecting batch mode support...")

                val rpmCommand =
                    allSensorCommands["0C"] as? RPMCommand
                        ?: return@withLock false.also {
                            Log.w(
                                TAG,
                                "RPM Command (0C) not found for batch mode detection."
                            )
                            batchModeDetectionRun = true
                            isBatchModeSupported = false
                        }
                val speedCommand =
                    allSensorCommands["0D"] as? SpeedCommand
                        ?: return@withLock false.also {
                            Log.w(
                                TAG,
                                "Speed Command (0D) not found for batch mode detection."
                            )
                            batchModeDetectionRun = true
                            isBatchModeSupported = false
                        }

                // Format a batch command (raw string)
                // ELM327 expects CR between commands in a batch, not just concatenated PIDs.
                // However, the old code was "01${rpmCommand.pid}\r01${speedCommand.pid}" which
                // seems like two commands.
                // OBD2Service.executeCommand handles one command string at a time.
                // True batching with one send and multiple replies is complex and not what
                // executeCommand is for.
                // For now, let's assume we are testing if the adapter *accepts* a string of two
                // commands separated by CR.
                // This will likely only yield the response for the *first* command if any.
                // A more robust batch test would be to send commands sequentially and check timing,
                // or see if a single non-standard command string yields multiple PID responses.
                // The current executeCommand is NOT designed for true batch responses (multiple
                // PIDs from one request string).

                // Test with a single, simple AT command first, as batching PIDs is complex with
                // current executeCommand
                val testAtCommand = "ATI" // A simple, fast AT command
                var receivedResponseForAt = false
                var atError = false

                Log.d(
                    TAG,
                    "Attempting simple AT command ($testAtCommand) to check responsiveness for batch test."
                )
                withTimeoutOrNull(1500L) {
                    obd2Service.executeAtCommand(testAtCommand).collect { response ->
                        Log.d(
                            TAG,
                            "Batch mode detection (AT test) response: ${response.decodedValue}"
                        )
                        if (response.isError) {
                            Log.w(TAG, "AT command for batch test failed: ${response.decodedValue}")
                            atError = true
                        } else {
                            receivedResponseForAt = true
                        }
                        throw CancellationException("Collected AT response for batch detection")
                    }
                }
                    ?: run {
                        Log.w(TAG, "AT command for batch test timed out.")
                        atError = true
                    }

                if (atError || !receivedResponseForAt) {
                    Log.w(
                        TAG,
                        "Simple AT command failed or timed out during batch detection. Assuming no batch support."
                    )
                    isBatchModeSupported = false
                } else {
                    // If simple AT command worked, then we can *infer* that more complex
                    // interactions *might* work.
                    // The original code sent "01${rpmCommand.pid}\r01${speedCommand.pid}".
                    // This is NOT true batching for ELM327 in a way that `executeCommand` would
                    // understand as a single request yielding multiple data segments.
                    // True ELM327 batching usually involves sending PIDs separated by spaces, not
                    // CR, if the adapter supports it (e.g. "010C0D05").
                    // And even then, the adapter sends responses one by one if headers are on.
                    // Given the limitations, the old test was likely not a true batch test.
                    // We'll simplify: if basic communication works, we'll assume the *potential*
                    // for sequential fast commands is there.
                    // For now, true OBD batching (multiple PIDs in one request, multiple distinct
                    // data responses) is out of scope for executeCommand.
                    Log.d(
                        TAG,
                        "Simple AT command succeeded. Current refactor does not support true PID batch command testing with executeCommand."
                    )
                    Log.i(
                        TAG,
                        "Marking batch mode as NOT SUPPORTED due to current limitations of executeCommand for true batch PID responses."
                    )
                    isBatchModeSupported =
                        false // Set to false as true batching is not supported by the new model
                    // easily.
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Cancellation during batch mode detection: ${e.message}")
                // isBatchModeSupported will retain its last set value or default (false)
            } catch (e: Exception) {
                Log.e(TAG, "Error detecting batch mode support", e)
                isBatchModeSupported = false
            }
        } // End of Mutex lock

        batchModeDetectionRun = true
        Log.d(TAG, "Batch mode detection complete. Supported: $isBatchModeSupported")
        return isBatchModeSupported
    }

    /**
     * Fast connection check that uses a timeout to avoid waiting too long.
     *
     * This method is used to check if the device is connected. It is used to check if the device is
     * connected.
     */
    private suspend fun isConnectedFast(): Boolean {
        return try {
            withTimeoutOrNull(CONNECTION_CHECK_TIMEOUT_MS) { bluetoothController.isConnected.value }
                ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Exception during connection check: ${e.message}")
            false
        }
    }

    /**
     * Updates the average query time with a new timing.
     *
     * This method is used to update the average query time with a new timing. It is used to update
     * the average query time with a new timing.
     */
    private fun updateAverageQueryTime(timeMs: Long) {
        if (timeMs <= 0 || timeMs > 1000) return // Skip outliers

        recentQueryTimings.add(timeMs)
        if (recentQueryTimings.size > MAX_TIMINGS) {
            recentQueryTimings.removeAt(0)
        }

        averageSensorQueryTimeMs = recentQueryTimings.average().toLong()

        // Keep within reasonable bounds
        if (averageSensorQueryTimeMs < 50) averageSensorQueryTimeMs = 50
        if (averageSensorQueryTimeMs > 250) averageSensorQueryTimeMs = 250
    }

    /**
     * Starts monitoring sensors with different priority levels at different refresh rates.
     *
     * This method is used to start monitoring sensors with different priority levels at different
     * refresh rates. It is used to start monitoring sensors with different priority levels at
     * different refresh rates.
     */
    override suspend fun startPrioritizedMonitoring(
        highPrioritySensors: List<String>,
        mediumPrioritySensors: List<String>,
        lowPrioritySensors: List<String>,
        baseRefreshRateMs: Long
    ) {
        if (isMonitoring) {
            Log.d(TAG, "Monitoring already active, stopping current monitoring")
            stopMonitoring()
            // Add a small delay to ensure cleanup completes
            delay(100)
        }

        isMonitoring = true

        // Begin with a detection of supported PIDs
        try {
            if (!supportDetectionRun) {
                detectSupportedPIDs()
            }
        } catch (e: Exception) {
            Log.w(
                TAG,
                "PID detection failed during prioritized monitoring start, continuing anyway",
                e
            )
        }

        // Disable batch mode for now as it's causing cancellation issues
        isBatchModeSupported = false
        batchModeDetectionRun = true

        // Send a sequence of commands to "prime" the adapter
        try {
            primeAdapter()
        } catch (e: Exception) {
            Log.w(TAG, "Adapter priming failed, continuing anyway", e)
        }

        // Log the different sensor groups
        Log.d(TAG, "Starting prioritized monitoring with:")
        Log.d(TAG, "High priority sensors (${baseRefreshRateMs}ms): $highPrioritySensors")
        Log.d(TAG, "Medium priority sensors (${baseRefreshRateMs * 2}ms): $mediumPrioritySensors")
        Log.d(TAG, "Low priority sensors (${baseRefreshRateMs * 3}ms): $lowPrioritySensors")

        // Between sensor delay in milliseconds - use adaptive delay based on averages
        val betweenSensorDelayMs = MIN_SENSOR_DELAY_MS

        // Set initial average sensor query time based on default refresh rate
        averageSensorQueryTimeMs = 25

        // Generate the round-robin polling sequence based on priority weights
        val pollingSequence = generatePollingSequence(
            highPrioritySensors,
            mediumPrioritySensors,
            lowPrioritySensors
        )

        // Start the monitoring coroutine with the supervisor job to prevent error propagation
        monitoringJob =
            sensorScope.launch {
                var currentIndex = 0

                while (isActive && isMonitoring) {
                    val cycleStartTime = System.currentTimeMillis()

                    if (!isConnectedFast()) {
                        Log.d(TAG, "Not connected, skipping sensor updates")
                        delay(300)
                        continue
                    }

                    try {
                        // Get the next sensor to poll in the round-robin sequence
                        val sensorId = pollingSequence[currentIndex]

                        // Process the sensor
                        processIndividualSensors(listOf(sensorId), betweenSensorDelayMs)

                        // Move to next sensor in the sequence
                        currentIndex = (currentIndex + 1) % pollingSequence.size

                        // Calculate remaining time in this update cycle
                        val cycleDuration = System.currentTimeMillis() - cycleStartTime
                        val remainingTime = baseRefreshRateMs - cycleDuration

                        // Log if cycle took too long
                        if (remainingTime < 0) {
                            Log.w(
                                TAG,
                                "Sensor polling cycle took longer than update interval: ${-remainingTime}ms over budget"
                            )
                        }

                        // Wait for the remaining time until next update cycle, with minimum wait
                        if (remainingTime > MIN_SENSOR_DELAY_MS) {
                            delay(remainingTime)
                        } else {
                            delay(MIN_SENSOR_DELAY_MS)
                        }
                    } catch (e: CancellationException) {
                        // Cancellation is expected during shutdown, so just break out of the
                        // loop
                        Log.d(TAG, "Monitoring loop was cancelled")
                        break
                    } catch (e: Exception) {
                        // For other exceptions, log and continue monitoring
                        Log.e(TAG, "Error in monitoring cycle", e)
                        delay(
                            100
                        ) // Add a short delay to avoid rapid retries in error situations
                    }
                }

                Log.d(TAG, "Prioritized sensor monitoring stopped")
            }
    }

    /**
     * Generates a round-robin polling sequence based on sensor priorities.
     * High priority sensors appear 6 times, medium 2 times, and low 1 time in the sequence.
     * All high priority sensors are given equal treatment for better real-time responsiveness.
     */
    private fun generatePollingSequence(
        highPrioritySensors: List<String>,
        mediumPrioritySensors: List<String>,
        lowPrioritySensors: List<String>
    ): List<String> {
        val sequence = mutableListOf<String>()

        // Extra entries for all high priority sensors
        // These will be polled more frequently for better responsiveness
        sequence.addAll(highPrioritySensors)

        // Ensure all sensors in same priority level are polled with same frequency
        // High priority sensors appear in 3 standard rounds
        repeat(3) {
            sequence.addAll(highPrioritySensors)
        }

        // Add high priority sensors again for extra responsiveness
        sequence.addAll(highPrioritySensors)

        // Medium priority sensors appear 2 times in the sequence
        repeat(2) {
            sequence.addAll(mediumPrioritySensors)
        }

        // Add high priority sensors once more for consistent responsiveness
        sequence.addAll(highPrioritySensors)

        // Low priority sensors appear 1 time in the sequence
        sequence.addAll(lowPrioritySensors)

        // Log the full sequence for debugging
        Log.d(TAG, "Generated round-robin polling sequence with ${sequence.size} entries")

        return sequence
    }

    /**
     * Processes sensors individually with a delay between each.
     *
     * This method is used to process sensors individually with a delay between each. It is used to
     * process sensors individually with a delay between each.
     */
    private suspend fun processIndividualSensors(sensorIds: List<String>, delayMs: Long) {
        for (sensorId in sensorIds) {
            try {
                if (allSensorCommands.containsKey(sensorId)) {
                    requestReading(sensorId)
                    if (sensorIds.size > 1) {
                        // delay(delayMs)
                    }
                }
            } catch (e: CancellationException) {
                // Log and continue with the next sensor
                Log.d(TAG, "Processing of sensor $sensorId was cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error monitoring sensor $sensorId", e)
            }
        }
    }
}
