package com.carsense.features.sensors.data.repository

import android.util.Log
import com.carsense.core.extensions.isAdapterInitializing
import com.carsense.features.bluetooth.domain.BluetoothController
import com.carsense.features.obd2.domain.OBD2Message
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

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
    private val MIN_SENSOR_DELAY_MS = 20L

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
        val startTime = System.currentTimeMillis()

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

            if (!isConnectedFast()) {
                Log.e(TAG, "Not connected to the vehicle")
                return Result.failure(IllegalStateException("Not connected to the vehicle"))
            }

            // Get the cached command string or create it if not cached
            val commandStr =
                commandStringCache[sensorId]
                    ?: run {
                        val str =
                            if (!command.getCommand().startsWith("01") &&
                                sensorId != "00"
                            ) {
                                "01${command.pid}"
                            } else {
                                command.getCommand()
                            }
                        commandStringCache[sensorId] = str
                        str
                    }

            // Use a timeout for the command to avoid waiting too long
            // Wrap in try-catch to handle cancellation exceptions
            var response: OBD2Message? = null
            try {
                response =
                    withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                        withContext(Dispatchers.IO) {
                            bluetoothController.sendOBD2Command(commandStr)
                        }
                    }
            } catch (e: CancellationException) {
                Log.w(TAG, "Command timeout or cancellation for sensor $sensorId")
                return Result.failure(e)
            } catch (e: Exception) {
                Log.e(TAG, "Exception while sending command for sensor $sensorId", e)
                return Result.failure(e)
            }

            if (response == null) {
                return Result.failure(IllegalStateException("No response received"))
            }

            // Check if we need to retry due to initialization state
            if (response.content.isAdapterInitializing()) {
                Log.d(TAG, "Adapter initializing, retrying request for $sensorId after delay")
                delay(200)

                try {
                    response =
                        withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                            withContext(Dispatchers.IO) {
                                bluetoothController.sendOBD2Command(commandStr)
                            }
                        }
                } catch (e: CancellationException) {
                    Log.w(TAG, "Retry command timeout or cancellation for sensor $sensorId")
                    return Result.failure(e)
                } catch (e: Exception) {
                    Log.e(TAG, "Exception while sending retry command for sensor $sensorId", e)
                    return Result.failure(e)
                }

                if (response == null) {
                    return Result.failure(IllegalStateException("No response received on retry"))
                }
            }

            val reading = command.parseResponse(response.content)

            // Skip invalid readings (like -1, FFFFFFFF, etc.)
            if (reading.value == "FFFFFFFF" ||
                reading.value == "-1" ||
                reading.value.contains("ERROR") ||
                reading.value.contains("UNABLE") ||
                reading.value.contains("NO DATA")
            ) {
                return Result.failure(
                    IllegalStateException("Invalid reading value: ${reading.value}")
                )
            }

            // Update the latest reading cache
            latestReadings[sensorId] = reading

            // Emit the reading to the flow
            _sensorReadings.emit(reading)

            // Update the timing statistics
            val queryTime = System.currentTimeMillis() - startTime
            updateAverageQueryTime(queryTime)

            Result.success(reading)
        } catch (e: CancellationException) {
            // Log but don't treat cancellation as an error - it's expected when jobs are cancelled
            Log.d(TAG, "Sensor reading cancelled for $sensorId")
            Result.failure(e)
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

    /**
     * Attempts to detect if the adapter supports batch mode
     * @return True if batch mode is detected as supported
     */
    private suspend fun detectBatchModeSupport(): Boolean {
        if (batchModeDetectionRun) {
            return isBatchModeSupported
        }

        try {
            Log.d(TAG, "Detecting batch mode support...")

            // Try a simple batch command with RPM and Speed which are commonly supported
            val rpmCommand = allSensorCommands["0C"] ?: return false
            val speedCommand = allSensorCommands["0D"] ?: return false

            // Format a batch command
            val batchCommand = "01${rpmCommand.pid}\r01${speedCommand.pid}"

            // Send the batch command
            val response = bluetoothController.sendOBD2Command(batchCommand)

            // If we get a response that doesn't contain errors, batch mode might be supported
            if (response != null &&
                !response.content.contains("ERROR") &&
                !response.content.contains("?") &&
                response.content.trim().isNotEmpty()
            ) {
                Log.d(TAG, "Batch mode might be supported. Response: ${response.content}")
                isBatchModeSupported = true
            } else {
                Log.d(TAG, "Batch mode likely not supported. Response: ${response?.content}")
                isBatchModeSupported = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting batch mode support", e)
            isBatchModeSupported = false
        }

        batchModeDetectionRun = true
        return isBatchModeSupported
    }

    /** Fast connection check that uses a timeout to avoid waiting too long */
    private suspend fun isConnectedFast(): Boolean {
        return try {
            withTimeoutOrNull(CONNECTION_CHECK_TIMEOUT_MS) { bluetoothController.isConnected.value }
                ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Exception during connection check: ${e.message}")
            false
        }
    }

    /** Update the average query time with a new timing */
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

    /** Starts monitoring sensors with different priority levels at different refresh rates */
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
        averageSensorQueryTimeMs = 120

        // Start the monitoring coroutine with the supervisor job to prevent error propagation
        monitoringJob =
            sensorScope.launch {
                // Counters to track update cycles for medium and low priority sensors
                var mediumPriorityCycleCounter = 0
                var lowPriorityCycleCounter = 0

                // Medium priority sensors are updated every 2 cycles
                val mediumPriorityUpdateInterval = 2

                // Low priority sensors are updated every 3 cycles
                val lowPriorityUpdateInterval = 3

                // Rotation indexes for sensor processing
                var highPriorityIndex = 0
                var mediumPriorityIndex = 0
                var lowPriorityIndex = 0

                while (isActive && isMonitoring) {
                    val cycleStartTime = System.currentTimeMillis()

                    if (!isConnectedFast()) {
                        Log.d(TAG, "Not connected, skipping sensor updates")
                        delay(300)
                        continue
                    }

                    try {
                        // Process high priority sensors (limited number per cycle)
                        if (highPrioritySensors.isNotEmpty()) {
                            // Determine number of sensors to process this cycle (adaptive based
                            // on timing)
                            val numHighToProcess =
                                min(
                                    MAX_HIGH_PRIORITY_SENSORS_PER_CYCLE,
                                    highPrioritySensors.size
                                )
                            val sensorsToProcess = mutableListOf<String>()

                            // Gather sensors to process in a round-robin fashion
                            repeat(numHighToProcess) {
                                sensorsToProcess.add(highPrioritySensors[highPriorityIndex])
                                highPriorityIndex =
                                    (highPriorityIndex + 1) % highPrioritySensors.size
                            }

                            processIndividualSensors(sensorsToProcess, betweenSensorDelayMs)
                        }

                        // Process medium priority sensors every other cycle
                        if (mediumPrioritySensors.isNotEmpty() &&
                            mediumPriorityCycleCounter %
                            mediumPriorityUpdateInterval == 0
                        ) {
                            // Process one medium priority sensor per cycle
                            val sensorId = mediumPrioritySensors[mediumPriorityIndex]
                            mediumPriorityIndex =
                                (mediumPriorityIndex + 1) % mediumPrioritySensors.size

                            processIndividualSensors(listOf(sensorId), betweenSensorDelayMs)
                        }

                        // Process low priority sensors every third cycle
                        if (lowPrioritySensors.isNotEmpty() &&
                            lowPriorityCycleCounter % lowPriorityUpdateInterval == 0
                        ) {
                            // Process one low priority sensor per cycle
                            val sensorId = lowPrioritySensors[lowPriorityIndex]
                            lowPriorityIndex = (lowPriorityIndex + 1) % lowPrioritySensors.size

                            processIndividualSensors(listOf(sensorId), betweenSensorDelayMs)
                        }

                        // Update counters
                        mediumPriorityCycleCounter =
                            (mediumPriorityCycleCounter + 1) %
                                    (mediumPriorityUpdateInterval * 2)
                        lowPriorityCycleCounter =
                            (lowPriorityCycleCounter + 1) % (lowPriorityUpdateInterval * 2)

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

                        // Wait for the remaining time until next update cycle, with minimum
                        // wait
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

    /** Process sensors individually with a delay between each */
    private suspend fun processIndividualSensors(sensorIds: List<String>, delayMs: Long) {
        for (sensorId in sensorIds) {
            try {
                if (allSensorCommands.containsKey(sensorId)) {
                    requestReading(sensorId)
                    if (sensorIds.size > 1) {
                        delay(delayMs)
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
