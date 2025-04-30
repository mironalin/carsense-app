package com.carsense.features.sensors.data.repository

import android.util.Log
import com.carsense.core.extensions.isAdapterInitializing
import com.carsense.features.bluetooth.domain.BluetoothController
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Implementation of SensorRepository that communicates with the vehicle's OBD system */
@Singleton
class SensorRepositoryImpl
@Inject
constructor(private val bluetoothController: BluetoothController) : SensorRepository {

    private val TAG = "SensorRepository"

    // Registry of all available sensor commands
    private val allSensorCommands = mutableMapOf<String, SensorCommand>()

    // Registry of supported sensor commands (confirmed by the vehicle)
    private val supportedSensorCommands = mutableMapOf<String, SensorCommand>()

    // Cache of the latest readings
    private val latestReadings = mutableMapOf<String, SensorReading>()

    // Shared flow of all sensor readings
    private val _sensorReadings = MutableSharedFlow<SensorReading>(replay = 0)

    // Flag to track if PID support detection has been run
    private var supportDetectionRun = false

    // Flag to track if monitoring is active
    private var isMonitoring = false

    // Coroutine job for the monitoring loop
    private var monitoringJob: Job? = null

    // Maximum number of retries for connection checks
    private val MAX_CONNECTION_RETRIES = 3

    init {
        // Register all sensor commands
        registerAllSensorCommands()
    }

    /** Register all available sensor commands */
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

        commands.forEach { command -> allSensorCommands[command.pid] = command }

        // Initially, populate the supported commands with all commands
        // They will be filtered after running PID support detection
        supportedSensorCommands.putAll(allSensorCommands)

        Log.d(TAG, "Registered ${allSensorCommands.size} sensor commands")
    }

    /**
     * Detects which PIDs are supported by the vehicle
     * @return True if detection succeeded, false otherwise
     */
    private suspend fun detectSupportedPIDs(): Boolean {
        if (!bluetoothController.isConnected.value) {
            Log.e(TAG, "Not connected to vehicle, cannot detect supported PIDs")
            return false
        }

        try {
            Log.d(TAG, "Detecting supported PIDs...")

            // Get the supported PIDs command
            val supportedPIDsCommand =
                allSensorCommands["00"] as? SupportedPIDsCommand ?: return false

            // Send the command to get supported PIDs
            val response = bluetoothController.sendOBD2Command(supportedPIDsCommand.getCommand())
            if (response == null) {
                Log.e(TAG, "No response from supported PIDs command")
                return false
            }

            // Parse the response to get supported PIDs
            val reading = supportedPIDsCommand.parseResponse(response.content)
            if (reading.isError) {
                Log.e(TAG, "Error detecting supported PIDs: ${reading.value}")
                return useHardcodedPIDSupport()
            }

            // Extract the list of supported PIDs
            val supportedPIDs = reading.value.split(",").filter { it.isNotEmpty() }
            Log.d(TAG, "Detected supported PIDs: $supportedPIDs")

            if (supportedPIDs.isEmpty()) {
                Log.w(TAG, "No PIDs reported as supported, using hardcoded fallbacks")
                return useHardcodedPIDSupport()
            }

            // Update the supported commands map
            supportedSensorCommands.clear()
            for (pid in supportedPIDs) {
                allSensorCommands[pid]?.let { command -> supportedSensorCommands[pid] = command }
            }

            // Always include the supported PIDs command itself
            supportedSensorCommands["00"] = supportedPIDsCommand

            Log.d(TAG, "Updated supported sensor commands: ${supportedSensorCommands.keys}")
            supportDetectionRun = true
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting supported PIDs", e)
            return useHardcodedPIDSupport()
        }
    }

    /**
     * Fallback method to use hardcoded PIDs that are commonly supported by most vehicles
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

    override fun getSensorReadings(sensorId: String): Flow<SensorReading> {
        return _sensorReadings.asSharedFlow().filter { it.pid == sensorId }
    }

    override suspend fun getLatestReading(sensorId: String): SensorReading? {
        return latestReadings[sensorId]
    }

    override suspend fun requestReading(sensorId: String): Result<SensorReading> {
        return try {
            // Ensure we've run PID support detection
            if (!supportDetectionRun) {
                detectSupportedPIDs()
            }

            // Get the command, first checking supported commands then falling back to all commands
            val command =
                supportedSensorCommands[sensorId]
                    ?: allSensorCommands[sensorId]
                    ?: throw IllegalArgumentException("Sensor not found: $sensorId")

            if (!bluetoothController.isConnected.value) {
                Log.e(TAG, "Not connected to the vehicle")
                return Result.failure(IllegalStateException("Not connected to the vehicle"))
            }

            Log.d(TAG, "Requesting reading for sensor $sensorId (${command.displayName})")

            // Make first attempt with proper mode prefix to ensure consistency
            val commandStr =
                if (!command.getCommand().startsWith("01") && sensorId != "00") {
                    "01${command.pid}"
                } else {
                    command.getCommand()
                }

            var response = bluetoothController.sendOBD2Command(commandStr)
            if (response == null) {
                Log.e(TAG, "No response received for sensor $sensorId")
                return Result.failure(IllegalStateException("No response received"))
            }

            // Check if we need to retry due to initialization state
            if (response.content.isAdapterInitializing()) {
                Log.d(TAG, "Adapter initializing, retrying request for $sensorId after delay")
                delay(500)
                response = bluetoothController.sendOBD2Command(commandStr)
                if (response == null) {
                    Log.e(TAG, "No response received on retry for sensor $sensorId")
                    return Result.failure(IllegalStateException("No response received on retry"))
                }
            }

            val reading = command.parseResponse(response.content)
            Log.d(
                TAG,
                "Received reading for ${command.displayName}: ${reading.value} ${reading.unit}"
            )

            // Skip invalid readings (like -1, FFFFFFFF, etc.)
            if (reading.value == "FFFFFFFF" ||
                reading.value == "-1" ||
                reading.value.contains("ERROR") ||
                reading.value.contains("UNABLE") ||
                reading.value.contains("NO DATA")
            ) {
                Log.w(TAG, "Invalid reading detected for ${command.displayName}: ${reading.value}")
                return Result.failure(
                    IllegalStateException("Invalid reading value: ${reading.value}")
                )
            }

            // Update the latest reading cache
            latestReadings[sensorId] = reading

            // Emit the reading to the flow
            _sensorReadings.emit(reading)

            Result.success(reading)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting reading for sensor $sensorId", e)
            Result.failure(e)
        }
    }

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

    override suspend fun getSupportedSensors(): List<String> {
        // Ensure we've run PID support detection
        if (!supportDetectionRun) {
            detectSupportedPIDs()
        }

        return supportedSensorCommands.keys.toList()
    }

    override suspend fun getSensorCategories(): List<SensorCategory> {
        return SensorCategories.getAllCategories()
    }

    override suspend fun getSensorsInCategory(categoryId: String): List<String> {
        val category = SensorCategories.getCategoryById(categoryId) ?: return emptyList()
        return category.sensorPids
    }

    override suspend fun detectSupportedSensors(forceRefresh: Boolean): Boolean {
        if (forceRefresh || !supportDetectionRun) {
            return detectSupportedPIDs()
        }
        return supportDetectionRun
    }

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
                val betweenSensorDelayMs = 50L

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
                        delay(50) // Use 50ms as minimum delay
                    }
                }

                Log.d(TAG, "Sensor monitoring stopped")
            }
    }

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

    override suspend fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }

        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null

        Log.d(TAG, "Stopped sensor monitoring")
    }

    /**
     * Check connection with retries
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
     * Prime the adapter with a single RPM request This helps initialize the adapter properly before
     * full monitoring begins
     */
    private suspend fun primeAdapter() {
        Log.d(TAG, "Priming adapter with RPM request")

        // Get the RPM command
        val rpmCommand = allSensorCommands["0C"] ?: return

        // Try multiple times with increasing delays
        val maxAttempts = 5
        var successful = false

        for (attempt in 1..maxAttempts) {
            try {
                Log.d(TAG, "Adapter priming attempt $attempt/$maxAttempts")

                // Send the RPM command
                val response = bluetoothController.sendOBD2Command(rpmCommand.getCommand())
                if (response == null) {
                    Log.w(TAG, "No response from adapter on priming attempt $attempt")
                    delay((500 * attempt).toLong()) // Increasing delay between attempts
                    continue
                }

                // Check if the response indicates adapter is still initializing
                if (!response.content.isAdapterInitializing()) {
                    Log.d(
                        TAG,
                        "Adapter responded normally on attempt $attempt: ${response.content}"
                    )
                    successful = true
                    break
                }

                Log.d(TAG, "Adapter still initializing on attempt $attempt, waiting longer")
                delay((1000 * attempt).toLong()) // Progressively longer delay
            } catch (e: Exception) {
                Log.e(TAG, "Error during adapter priming attempt $attempt: ${e.message}")
                delay((500 * attempt).toLong())
            }
        }

        if (successful) {
            Log.d(TAG, "Adapter priming completed successfully")
        } else {
            Log.w(TAG, "Adapter priming completed with partial success after $maxAttempts attempts")
            // Continue anyway, as the adapter might be ready for some commands
        }
    }
}
