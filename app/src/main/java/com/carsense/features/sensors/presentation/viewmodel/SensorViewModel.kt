package com.carsense.features.sensors.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.domain.repository.SensorRepository
import com.carsense.features.sensors.data.service.SensorSnapshotCollector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.flow.collectLatest

/** Enum defining sensor priority levels */
enum class SensorPriority {
    HIGH, // Critical sensors that update most frequently (e.g., RPM, speed)
    MEDIUM, // Important sensors that update at medium frequency (e.g., throttle, temp)
    LOW // Non-critical sensors that update less frequently (e.g., fuel level)
}

/** Data class to hold sensor priority configuration */
data class PrioritizedSensor(val pid: String, val priority: SensorPriority)

/** State container for the Sensors screen */
data class SensorState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val rpmReading: SensorReading? = null,
    val speedReading: SensorReading? = null,
    val coolantTempReading: SensorReading? = null,
    val intakeAirTempReading: SensorReading? = null,
    val throttlePositionReading: SensorReading? = null,
    val fuelLevelReading: SensorReading? = null,
    val engineLoadReading: SensorReading? = null,
    val intakeManifoldPressureReading: SensorReading? = null,
    val timingAdvanceReading: SensorReading? = null,
    val massAirFlowReading: SensorReading? = null,
    val isMonitoring: Boolean = false,
    // Add new fields for snapshot status
    val snapshotUploadStatus: SnapshotUploadStatus = SnapshotUploadStatus.NONE,
    val snapshotUploadError: String? = null,
    val snapshotCollectionInProgress: Boolean = false,
    val snapshotReadingsCount: Int = 0,
    val snapshotLastUploadTimestamp: Long = 0,
    val snapshotLastUploadedReadingsCount: Int = 0
)

/** Enum for snapshot upload status */
enum class SnapshotUploadStatus {
    NONE,
    SUCCESS,
    ERROR
}

/** ViewModel for the Sensors screen that manages sensor reading states */
@HiltViewModel
class SensorViewModel @Inject constructor(
    private val sensorRepository: SensorRepository,
    private val sensorSnapshotCollector: SensorSnapshotCollector
) : ViewModel() {

    private val _state = MutableStateFlow(SensorState())
    val state: StateFlow<SensorState> = _state.asStateFlow()

    // Constants for sensor PIDs
    companion object {
        const val PID_RPM = "0C"
        const val PID_SPEED = "0D"
        const val PID_COOLANT_TEMP = "05"
        const val PID_INTAKE_AIR_TEMP = "0F"
        const val PID_THROTTLE_POSITION = "11"
        const val PID_FUEL_LEVEL = "2F"
        const val PID_ENGINE_LOAD = "04"
        const val PID_INTAKE_MANIFOLD_PRESSURE = "0B"
        const val PID_TIMING_ADVANCE = "0E"
        const val PID_MAF_RATE = "10"

        // More conservative priority multipliers for better reliability
        const val HIGH_PRIORITY_FACTOR = 1.0 // High priority sensors refresh at base rate
        const val MEDIUM_PRIORITY_FACTOR = 2.0 // Medium priority sensors refresh at 2x base rate
        const val LOW_PRIORITY_FACTOR =
            3.0 // Faster updates for low priority sensors (reduced from 4x)

        // List of sensors with their priority levels
        val PRIORITIZED_SENSORS =
            listOf(
                // High priority - most frequently updated
                PrioritizedSensor(PID_RPM, SensorPriority.HIGH),
                PrioritizedSensor(PID_SPEED, SensorPriority.HIGH),
                PrioritizedSensor(PID_THROTTLE_POSITION, SensorPriority.HIGH),

                // Medium priority - regularly updated
                PrioritizedSensor(PID_COOLANT_TEMP, SensorPriority.MEDIUM),
                PrioritizedSensor(PID_ENGINE_LOAD, SensorPriority.MEDIUM),
                PrioritizedSensor(PID_INTAKE_MANIFOLD_PRESSURE, SensorPriority.MEDIUM),

                // Low priority - less frequently updated
                PrioritizedSensor(PID_INTAKE_AIR_TEMP, SensorPriority.LOW),
                PrioritizedSensor(PID_FUEL_LEVEL, SensorPriority.LOW),
                PrioritizedSensor(PID_TIMING_ADVANCE, SensorPriority.LOW),
                PrioritizedSensor(PID_MAF_RATE, SensorPriority.LOW)
            )

        // Helper method to get sensor priority
        fun getSensorPriority(pid: String): SensorPriority {
            return PRIORITIZED_SENSORS.find { it.pid == pid }?.priority ?: SensorPriority.LOW
        }

        // Helper methods to get sensors by priority
        fun getHighPrioritySensors(): List<String> {
            return PRIORITIZED_SENSORS.filter { it.priority == SensorPriority.HIGH }.map { it.pid }
        }

        fun getMediumPrioritySensors(): List<String> {
            return PRIORITIZED_SENSORS.filter { it.priority == SensorPriority.MEDIUM }.map {
                it.pid
            }
        }

        fun getLowPrioritySensors(): List<String> {
            return PRIORITIZED_SENSORS.filter { it.priority == SensorPriority.LOW }.map { it.pid }
        }

        // Valid sensor value ranges - minimum values and default maximums
        // Maximum values will be dynamically adjusted based on observed readings
        val SENSOR_MIN_VALUES =
            mapOf(
                PID_RPM to 0.0, // RPM cannot be negative
                PID_SPEED to 0.0, // Speed cannot be negative
                PID_THROTTLE_POSITION to 0.0, // Throttle position cannot be negative
                PID_COOLANT_TEMP to -40.0, // Coolant temp can be negative in cold climates
                PID_INTAKE_AIR_TEMP to
                        -40.0, // Intake air temp can be negative in cold climates
                PID_ENGINE_LOAD to 0.0, // Engine load cannot be negative
                PID_FUEL_LEVEL to 0.0, // Fuel level cannot be negative
                PID_INTAKE_MANIFOLD_PRESSURE to 0.0, // Pressure cannot be negative
                PID_TIMING_ADVANCE to
                        -64.0, // Timing advance can be negative (retarded timing)
                PID_MAF_RATE to 0.0 // MAF rate cannot be negative
            )
    }

    // Track maximum observed values for each sensor to set dynamic limits
    private val maxObservedValues =
        mutableMapOf<String, Double>().apply {
            // Initialize with reasonable defaults
            put(PID_RPM, 10000.0) // High enough for most simulators
            put(PID_SPEED, 300.0)
            put(PID_THROTTLE_POSITION, 100.0)
            put(PID_COOLANT_TEMP, 215.0)
            put(PID_INTAKE_AIR_TEMP, 215.0)
            put(PID_ENGINE_LOAD, 100.0)
            put(PID_FUEL_LEVEL, 100.0)
            put(PID_INTAKE_MANIFOLD_PRESSURE, 255.0)
            put(PID_TIMING_ADVANCE, 64.0)
            put(PID_MAF_RATE, 655.35)
        }

    // Maps to store recent readings for smoothing and prediction
    private val rpmHistory = LinkedList<Pair<Long, Double>>()
    private val speedHistory = LinkedList<Pair<Long, Double>>()
    private val throttleHistory = LinkedList<Pair<Long, Double>>()
    private val tempHistory = LinkedList<Pair<Long, Double>>()
    private val loadHistory = LinkedList<Pair<Long, Double>>()
    private val pressureHistory = LinkedList<Pair<Long, Double>>()
    private val fuelHistory = LinkedList<Pair<Long, Double>>()
    private val timingHistory = LinkedList<Pair<Long, Double>>()
    private val airflowHistory = LinkedList<Pair<Long, Double>>()
    private val airTempHistory = LinkedList<Pair<Long, Double>>()

    // Maximum readings to keep in history
    private val MAX_HISTORY_SIZE = 7

    /** Coroutine job for monitoring recovery */
    private var monitoringRecoveryJob: Job? = null

    /** Coroutine job for providing intermediate updates */
    private var interpolationJob: Job? = null

    // Maps to store current and target values for animation
    private val currentValues = mutableMapOf<String, Double>()
    private val targetValues = mutableMapOf<String, Double>()

    // Track which sensors are currently being monitored
    private var currentlySelectedSensors: Set<String>? = null

    // Removed complex animation-related fields that were causing issues
    // private val sensorVelocities = mutableMapOf<String, Double>()
    // private val lastActualReadings = mutableMapOf<String, Double>()

    /** Starts monitoring for sensor data */
    fun startMonitoring() {
        if (state.value.isMonitoring) {
            return
        }

        _state.update { it.copy(isLoading = true, error = null, isMonitoring = true) }

        monitoringRecoveryJob?.cancel()

        // Start with priority-based monitoring
        startPriorityMonitoring()
    }

    /** Starts monitoring for only selected sensor data */
    fun startSelectiveMonitoring(selectedSensorIds: Set<String>) {
        android.util.Log.d("SensorViewModel", "startSelectiveMonitoring called with PIDs: $selectedSensorIds")
        
        if (state.value.isMonitoring) {
            android.util.Log.d("SensorViewModel", "Already monitoring, stopping first...")
            stopMonitoring()
        }
        
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                startSelectivePriorityMonitoring(selectedSensorIds)
            } catch (e: Exception) {
                handleError("Failed to start selective monitoring: ${e.message}")
            }
        }
    }

    /** Stops monitoring for sensor data */
    fun stopMonitoring() {
        if (!state.value.isMonitoring) {
            return
        }

        monitoringRecoveryJob?.cancel()
        monitoringRecoveryJob = null

        // Clear the selected sensors since we're stopping monitoring
        currentlySelectedSensors = null

        viewModelScope.launch {
            try {
                sensorRepository.stopMonitoring()
                _state.update { it.copy(isMonitoring = false) }
            } catch (e: Exception) {
                handleError("Failed to stop monitoring: ${e.message}")
            }
        }
    }

    /** Implements priority-based monitoring with error recovery */
    private fun startPriorityMonitoring() {
        viewModelScope.launch {
            try {
                // Set to monitor all sensors
                currentlySelectedSensors = null

                // Group sensors by priority
                val highPrioritySensors = listOf(PID_RPM, PID_SPEED, PID_THROTTLE_POSITION)
                val mediumPrioritySensors =
                    listOf(PID_COOLANT_TEMP, PID_ENGINE_LOAD, PID_INTAKE_MANIFOLD_PRESSURE)
                val lowPrioritySensors =
                    listOf(
                        PID_INTAKE_AIR_TEMP,
                        PID_FUEL_LEVEL,
                        PID_TIMING_ADVANCE,
                        PID_MAF_RATE
                    )

                // Use a fixed refresh rate of 1ms instead of the dynamic one
                val baseRefreshRate = 200L

                // Set up monitoring with prioritization
                sensorRepository.startPrioritizedMonitoring(
                    highPrioritySensors,
                    mediumPrioritySensors,
                    lowPrioritySensors,
                    baseRefreshRate
                )

                // Set up flows to collect readings and update UI state
                setupSensorFlows()

                // Set up error recovery monitoring
                setupRecoveryMonitoring()

                // Start the interpolation job for predictive updates
                startInterpolationJob()

                // Loading is complete
                _state.update { it.copy(isLoading = false) }
            } catch (e: CancellationException) {
                // Handle cancellation - this is normal during app shutdown
                _state.update { it.copy(isLoading = false, isMonitoring = false) }
            } catch (e: Exception) {
                // Handle any exceptions that occur during monitoring
                handleError("Failed to start monitoring: ${e.message}")
                // Try to restart monitoring after delay - this helps with transient errors
                setupRecoveryMonitoring()
            }
        }
    }

    /** Implements priority-based monitoring with error recovery for selected sensors only */
    private fun startSelectivePriorityMonitoring(selectedSensorIds: Set<String>) {
        android.util.Log.d("SensorViewModel", "startSelectivePriorityMonitoring called with: $selectedSensorIds")
        
        viewModelScope.launch {
            try {
                // Set the currently selected sensors
                currentlySelectedSensors = selectedSensorIds

                // Group selected sensors by priority
                val allHighPrioritySensors = listOf(PID_RPM, PID_SPEED, PID_THROTTLE_POSITION)
                val allMediumPrioritySensors =
                    listOf(PID_COOLANT_TEMP, PID_ENGINE_LOAD, PID_INTAKE_MANIFOLD_PRESSURE)
                val allLowPrioritySensors =
                    listOf(
                        PID_INTAKE_AIR_TEMP,
                        PID_FUEL_LEVEL,
                        PID_TIMING_ADVANCE,
                        PID_MAF_RATE
                    )

                // Filter by selected sensors
                val highPrioritySensors = allHighPrioritySensors.filter { selectedSensorIds.contains(it) }
                val mediumPrioritySensors = allMediumPrioritySensors.filter { selectedSensorIds.contains(it) }
                val lowPrioritySensors = allLowPrioritySensors.filter { selectedSensorIds.contains(it) }

                android.util.Log.d("SensorViewModel", "Filtered high priority: $highPrioritySensors")
                android.util.Log.d("SensorViewModel", "Filtered medium priority: $mediumPrioritySensors")
                android.util.Log.d("SensorViewModel", "Filtered low priority: $lowPrioritySensors")

                // Set up flows for only the selected sensors
                setupSensorFlows(selectedSensorIds)

                // Start the interpolation job for smoother UI updates
                startInterpolationJob()

                // Start repository monitoring with filtered sensor lists
                sensorRepository.startPrioritizedMonitoring(
                    highPrioritySensors = highPrioritySensors,
                    mediumPrioritySensors = mediumPrioritySensors,
                    lowPrioritySensors = lowPrioritySensors
                )

                _state.update { it.copy(isMonitoring = true, isLoading = false, error = null) }
            } catch (e: CancellationException) {
                // Normal during shutdown
                _state.update { it.copy(isLoading = false, isMonitoring = false) }
            } catch (e: Exception) {
                // Log but don't crash
                println("Error in selective recovery monitoring: ${e.message}")
                // Try to restart monitoring after delay - this helps with transient errors
                setupSelectiveRecoveryMonitoring(selectedSensorIds)
            }
        }
    }

    /** Set up flows for sensors to collect readings and update UI state */
    private fun setupSensorFlows(selectedSensorIds: Set<String>? = null) {
        // Create a map of all available sensor flows
        val allSensorFlows =
            mapOf(
                PID_RPM to sensorRepository.getSensorReadings(PID_RPM),
                PID_SPEED to sensorRepository.getSensorReadings(PID_SPEED),
                PID_COOLANT_TEMP to sensorRepository.getSensorReadings(PID_COOLANT_TEMP),
                PID_INTAKE_AIR_TEMP to
                        sensorRepository.getSensorReadings(PID_INTAKE_AIR_TEMP),
                PID_THROTTLE_POSITION to
                        sensorRepository.getSensorReadings(PID_THROTTLE_POSITION),
                PID_FUEL_LEVEL to sensorRepository.getSensorReadings(PID_FUEL_LEVEL),
                PID_ENGINE_LOAD to sensorRepository.getSensorReadings(PID_ENGINE_LOAD),
                PID_INTAKE_MANIFOLD_PRESSURE to
                        sensorRepository.getSensorReadings(PID_INTAKE_MANIFOLD_PRESSURE),
                PID_TIMING_ADVANCE to
                        sensorRepository.getSensorReadings(PID_TIMING_ADVANCE),
                PID_MAF_RATE to sensorRepository.getSensorReadings(PID_MAF_RATE)
            )

        // Filter flows based on selected sensors (if provided)
        val sensorFlows = if (selectedSensorIds != null) {
            allSensorFlows.filterKeys { selectedSensorIds.contains(it) }
        } else {
            allSensorFlows
        }

        Log.d("SensorViewModel", "Setting up flows for sensors: ${sensorFlows.keys}")

        // Collect from each flow and update state
        sensorFlows.forEach { (pid, flow) ->
            viewModelScope.launch { flow.collect { reading -> updateReadingState(pid, reading) } }
        }
    }

    /** Update state based on the sensor PID */
    private fun updateReadingState(pid: String, reading: SensorReading) {
        // Add to history for animation
        addToHistory(pid, reading)

        // Apply smoothing for key sensors
        val smoothedReading = applySmoothingIfNeeded(pid, reading)

        _state.update { state ->
            when (pid) {
                PID_RPM -> state.copy(rpmReading = smoothedReading, isLoading = false)
                PID_SPEED -> state.copy(speedReading = smoothedReading)
                PID_COOLANT_TEMP -> state.copy(coolantTempReading = smoothedReading)
                PID_INTAKE_AIR_TEMP -> state.copy(intakeAirTempReading = smoothedReading)
                PID_THROTTLE_POSITION -> state.copy(throttlePositionReading = smoothedReading)
                PID_FUEL_LEVEL -> state.copy(fuelLevelReading = smoothedReading)
                PID_ENGINE_LOAD -> state.copy(engineLoadReading = smoothedReading)
                PID_INTAKE_MANIFOLD_PRESSURE ->
                    state.copy(intakeManifoldPressureReading = smoothedReading)

                PID_TIMING_ADVANCE -> state.copy(timingAdvanceReading = smoothedReading)
                PID_MAF_RATE -> state.copy(massAirFlowReading = smoothedReading)
                else -> state
            }
        }
    }

    /**
     * Updates the maximum observed value for a sensor if the new value is higher This allows for
     * dynamic adjustment of upper limits based on actual readings
     */
    private fun updateMaxObservedValue(pid: String, value: Double) {
        val currentMax = maxObservedValues[pid] ?: return

        // If value is within a reasonable range and higher than current max, update it
        // For RPM specifically, we want to be permissive to capture simulator max values
        val isReasonable =
            when (pid) {
                PID_RPM -> value <= 12000.0 // Allow up to 12000 RPM as a hard ceiling
                PID_SPEED -> value <= 400.0 // Allow up to 400 km/h
                PID_THROTTLE_POSITION -> value <= 102.0 // Allow slightly over 100%
                PID_COOLANT_TEMP -> value <= 220.0 // Allow slightly over normal max
                else -> true // For other sensors, be permissive
            }

        if (isReasonable && value > currentMax) {
            maxObservedValues[pid] = value + 10.0 // Add a small buffer above the max
            Log.d("SensorViewModel", "Updated max value for $pid to ${value + 10.0}")
        }
    }

    /**
     * Validate and clamp the sensor value within valid ranges to prevent invalid readings Uses
     * dynamic maximum values based on observed readings
     */
    private fun validateSensorValue(pid: String, value: Double): Double {
        val min = SENSOR_MIN_VALUES[pid] ?: 0.0
        val max = maxObservedValues[pid] ?: Double.MAX_VALUE

        // Log when values need to be clamped (for debugging)
        if (value < min) {
            Log.d("SensorViewModel", "Clamping $pid value from $value to $min (below minimum)")
            return min
        }
        if (value > max) {
            Log.d("SensorViewModel", "Clamping $pid value from $value to $max (above maximum)")
            return max
        }

        return value
    }

    /**
     * Apply smoothing to readings based on recent history This prevents jumpy values from UI
     * perspective
     */
    private fun applySmoothingIfNeeded(pid: String, reading: SensorReading): SensorReading {
        val valueStr = reading.value
        val value = valueStr.toDoubleOrNull() ?: return reading

        // Ensure value is within valid range
        val validValue = validateSensorValue(pid, value)

        // If the value needed to be clamped, create a new reading with the corrected value
        if (validValue != value) {
            return reading.copy(value = validValue.toInt().toString())
        }

        // Apply smoothing based on sensor type
        val smoothedValue =
            when (pid) {
                PID_RPM -> smoothValue(validValue, rpmHistory)
                PID_SPEED -> smoothValue(validValue, speedHistory)
                PID_THROTTLE_POSITION -> smoothValue(validValue, throttleHistory)
                PID_COOLANT_TEMP -> smoothValue(validValue, tempHistory)
                PID_ENGINE_LOAD -> smoothValue(validValue, loadHistory)
                PID_INTAKE_MANIFOLD_PRESSURE -> smoothValue(validValue, pressureHistory)
                PID_FUEL_LEVEL -> smoothValue(validValue, fuelHistory)
                PID_TIMING_ADVANCE -> smoothValue(validValue, timingHistory)
                PID_MAF_RATE -> smoothValue(validValue, airflowHistory)
                PID_INTAKE_AIR_TEMP -> smoothValue(validValue, airTempHistory)
                else -> validValue
            }

        // Only apply smoothing if the difference is reasonable
        return if (abs(smoothedValue - validValue) > getMaxSmoothingDelta(pid)) {
            reading.copy(value = validValue.toInt().toString())
        } else {
            reading.copy(value = smoothedValue.toInt().toString())
        }
    }

    /**
     * Determines if smoothing should be applied to a sensor Now always returns true since we want
     * to smooth all sensors
     */
    private fun shouldApplySmoothing(pid: String): Boolean {
        return true
    }

    /** Apply weighted average smoothing to a value */
    private fun smoothValue(currentValue: Double, history: List<Pair<Long, Double>>): Double {
        if (history.size < 2) return currentValue

        // Apply weighted averaging with timestamp-based weights for smoother transitions
        var totalWeight = 0.0
        var weightedSum = 0.0
        val now = System.currentTimeMillis()
        val maxAge = 2000L // Consider readings from last 2 seconds

        // First apply a gentler 1.1 exponent to avoid overemphasis
        history.forEachIndexed { index, (timestamp, value) ->
            // Time-based weight - more recent readings get higher weight
            val age = now - timestamp
            val timeWeight = max(0.1, 1.0 - (age.toDouble() / maxAge))

            // Combine with position-based weight (newer items in list have higher index)
            val weight = timeWeight * (1.1).pow(index.toDouble())

            weightedSum += value * weight
            totalWeight += weight
        }

        // Calculate a smoothed result that weights recent values higher but not excessively
        return (weightedSum / totalWeight)
    }

    /**
     * Gets the maximum allowed difference for smoothing Prevents smoothing from hiding genuine
     * large changes
     */
    private fun getMaxSmoothingDelta(pid: String): Double {
        return when (pid) {
            PID_RPM -> 250.0 // Increased from 200 to 250 RPM for smoother transitions
            PID_SPEED -> 4.0 // Increased from 3 to 4 km/h for smoother transitions
            PID_THROTTLE_POSITION -> 10.0 // Increased from 8 to 10% for smoother transitions
            PID_COOLANT_TEMP -> 4.0 // Increased from 3 to 4°C for smoother transitions
            PID_INTAKE_AIR_TEMP -> 4.0 // Increased from 3 to 4°C for smoother transitions
            PID_ENGINE_LOAD -> 10.0 // Increased from 8 to 10% for smoother transitions
            PID_FUEL_LEVEL -> 4.0 // Increased from 3 to 4% for smoother transitions
            PID_INTAKE_MANIFOLD_PRESSURE ->
                10.0 // Increased from 8 to 10 kPa for smoother transitions
            PID_TIMING_ADVANCE -> 4.0 // Increased from 3 to 4 degrees for smoother transitions
            PID_MAF_RATE -> 10.0 // Increased from 8 to 10 g/s for smoother transitions
            else -> 10.0 // Increased from 8 to 10 for smoother transitions
        }
    }

    /**
     * Starts the interpolation job that provides intermediate updates to UI for smoother
     * transitions
     */
    private fun startInterpolationJob() {
        interpolationJob?.cancel()

        interpolationJob =
            viewModelScope.launch {
                while (isActive && state.value.isMonitoring) {
                    // Use a higher frame rate for smoother animation
                    delay(8) // ~120fps for extra smooth animation (was 16 - 60fps)

                    try {
                        // Keep track of which sensors need updates
                        val updates = mutableMapOf<String, SensorReading>()

                        // Get the list of sensors to process
                        val sensorsToProcess = currentlySelectedSensors ?: setOf(
                            PID_RPM, PID_SPEED, PID_THROTTLE_POSITION, PID_COOLANT_TEMP, 
                            PID_ENGINE_LOAD, PID_INTAKE_MANIFOLD_PRESSURE, PID_FUEL_LEVEL, 
                            PID_TIMING_ADVANCE, PID_MAF_RATE, PID_INTAKE_AIR_TEMP
                        )

                        // Process only selected sensors
                        sensorsToProcess.forEach { sensorId ->
                            when (sensorId) {
                                PID_RPM -> processRpmAnimation(updates)
                                PID_SPEED -> processSpeedAnimation(updates)
                                PID_THROTTLE_POSITION -> processGenericSensorAnimation(PID_THROTTLE_POSITION, updates)
                                PID_COOLANT_TEMP -> processGenericSensorAnimation(PID_COOLANT_TEMP, updates)
                                PID_ENGINE_LOAD -> processGenericSensorAnimation(PID_ENGINE_LOAD, updates)
                                PID_INTAKE_MANIFOLD_PRESSURE -> processGenericSensorAnimation(PID_INTAKE_MANIFOLD_PRESSURE, updates)
                                PID_FUEL_LEVEL -> processGenericSensorAnimation(PID_FUEL_LEVEL, updates)
                                PID_TIMING_ADVANCE -> processGenericSensorAnimation(PID_TIMING_ADVANCE, updates)
                                PID_MAF_RATE -> processGenericSensorAnimation(PID_MAF_RATE, updates)
                                PID_INTAKE_AIR_TEMP -> processGenericSensorAnimation(PID_INTAKE_AIR_TEMP, updates)
                            }
                        }

                        // Apply all updates in a single state update
                        if (updates.isNotEmpty()) {
                            _state.update { currentState ->
                                var newState = currentState

                                updates[PID_RPM]?.let {
                                    newState = newState.copy(rpmReading = it)
                                }
                                updates[PID_SPEED]?.let {
                                    newState = newState.copy(speedReading = it)
                                }
                                updates[PID_THROTTLE_POSITION]?.let {
                                    newState = newState.copy(throttlePositionReading = it)
                                }
                                updates[PID_COOLANT_TEMP]?.let {
                                    newState = newState.copy(coolantTempReading = it)
                                }
                                updates[PID_ENGINE_LOAD]?.let {
                                    newState = newState.copy(engineLoadReading = it)
                                }
                                updates[PID_INTAKE_MANIFOLD_PRESSURE]?.let {
                                    newState = newState.copy(intakeManifoldPressureReading = it)
                                }
                                updates[PID_FUEL_LEVEL]?.let {
                                    newState = newState.copy(fuelLevelReading = it)
                                }
                                updates[PID_TIMING_ADVANCE]?.let {
                                    newState = newState.copy(timingAdvanceReading = it)
                                }
                                updates[PID_MAF_RATE]?.let {
                                    newState = newState.copy(massAirFlowReading = it)
                                }
                                updates[PID_INTAKE_AIR_TEMP]?.let {
                                    newState = newState.copy(intakeAirTempReading = it)
                                }

                                newState
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SensorViewModel", "Animation error: ${e.message}")
                    }
                }
            }
    }

    /** Process RPM animation specifically */
    private fun processRpmAnimation(updates: MutableMap<String, SensorReading>) {
        val reading = state.value.rpmReading ?: return
        if (rpmHistory.isEmpty()) return

        // Get the real target value
        val targetRpm = rpmHistory.lastOrNull()?.second ?: return
        targetValues[PID_RPM] = targetRpm

        // Initialize current value if needed
        val currentRpm =
            currentValues[PID_RPM]
                ?: run {
                    val initialValue = reading.value.toDoubleOrNull() ?: targetRpm
                    currentValues[PID_RPM] = initialValue
                    initialValue
                }

        // Calculate distance to target
        val distance = targetRpm - currentRpm

        // If we're close enough, snap to the target
        if (abs(distance) < 0.5) {
            if (currentValues[PID_RPM] != targetRpm) {
                currentValues[PID_RPM] = targetRpm
                updates[PID_RPM] =
                    reading.copy(
                        value = targetRpm.toInt().toString(),
                        timestamp = System.currentTimeMillis()
                    )
            }
            return
        }

        // Calculate animation speed based on distance
        // For RPM we want faster movement for larger distances
        val baseSpeed = 100.0 // Increased for faster RPM changes (was 60.0)
        val speedMultiplier =
            min(
                1.0 + (abs(distance) / 400.0),
                12.0
            ) // Increased speed for larger distances (was 8.0 max)
        val speed = baseSpeed * speedMultiplier

        // Calculate new RPM value
        val movement = if (distance > 0) min(speed, distance) else max(-speed, distance)
        val newRpm = currentRpm + movement

        // Update current value
        currentValues[PID_RPM] = newRpm

        // Create new reading with animated value
        updates[PID_RPM] =
            reading.copy(
                value = newRpm.toInt().toString(),
                timestamp = System.currentTimeMillis()
            )
    }

    /** Process Speed animation specifically */
    private fun processSpeedAnimation(updates: MutableMap<String, SensorReading>) {
        val reading = state.value.speedReading ?: return
        if (speedHistory.isEmpty()) return

        // Get the real target value
        val targetSpeed = speedHistory.lastOrNull()?.second ?: return
        targetValues[PID_SPEED] = targetSpeed

        // Initialize current value if needed
        val currentSpeed =
            currentValues[PID_SPEED]
                ?: run {
                    val initialValue = reading.value.toDoubleOrNull() ?: targetSpeed
                    currentValues[PID_SPEED] = initialValue
                    initialValue
                }

        // Calculate distance to target
        val distance = targetSpeed - currentSpeed

        // If we're close enough, snap to the target
        if (abs(distance) < 0.5) {
            if (currentValues[PID_SPEED] != targetSpeed) {
                currentValues[PID_SPEED] = targetSpeed
                updates[PID_SPEED] =
                    reading.copy(
                        value = targetSpeed.toInt().toString(),
                        timestamp = System.currentTimeMillis()
                    )
            }
            return
        }

        // Speedometers should respond quickly to changes
        val baseSpeed =
            if (distance > 0) 1.2 else 2.0 // Increased for faster response (was 0.8/1.5)
        val speedMultiplier =
            min(
                1.0 + (abs(distance) / 20.0),
                5.0
            ) // Increased boost for larger changes (was 3.0 max)
        val speed = baseSpeed * speedMultiplier

        // Calculate new speed value
        val movement = if (distance > 0) min(speed, distance) else max(-speed, distance)
        val newSpeed = currentSpeed + movement

        // Update current value
        currentValues[PID_SPEED] = newSpeed

        // Create new reading with animated value
        updates[PID_SPEED] =
            reading.copy(
                value = newSpeed.toInt().toString(),
                timestamp = System.currentTimeMillis()
            )
    }

    /** Process animation for other sensors */
    private fun processGenericSensorAnimation(
        pid: String,
        updates: MutableMap<String, SensorReading>
    ) {
        // Get current reading
        val reading =
            when (pid) {
                PID_THROTTLE_POSITION -> state.value.throttlePositionReading
                PID_COOLANT_TEMP -> state.value.coolantTempReading
                PID_ENGINE_LOAD -> state.value.engineLoadReading
                PID_INTAKE_MANIFOLD_PRESSURE -> state.value.intakeManifoldPressureReading
                PID_FUEL_LEVEL -> state.value.fuelLevelReading
                PID_TIMING_ADVANCE -> state.value.timingAdvanceReading
                PID_MAF_RATE -> state.value.massAirFlowReading
                PID_INTAKE_AIR_TEMP -> state.value.intakeAirTempReading
                else -> null
            }
                ?: return

        // Get sensor history
        val history =
            when (pid) {
                PID_THROTTLE_POSITION -> throttleHistory
                PID_COOLANT_TEMP -> tempHistory
                PID_ENGINE_LOAD -> loadHistory
                PID_INTAKE_MANIFOLD_PRESSURE -> pressureHistory
                PID_FUEL_LEVEL -> fuelHistory
                PID_TIMING_ADVANCE -> timingHistory
                PID_MAF_RATE -> airflowHistory
                PID_INTAKE_AIR_TEMP -> airTempHistory
                else -> return
            }

        if (history.isEmpty()) return

        // Get target value
        val targetValue = history.lastOrNull()?.second ?: return
        targetValues[pid] = targetValue

        // Initialize current value if needed
        val currentValue =
            currentValues[pid]
                ?: run {
                    val initialValue = reading.value.toDoubleOrNull() ?: targetValue
                    currentValues[pid] = initialValue
                    initialValue
                }

        // Calculate distance to target
        val distance = targetValue - currentValue

        // If we're close enough, snap to the target
        if (abs(distance) < 0.5) {
            if (currentValues[pid] != targetValue) {
                currentValues[pid] = targetValue
                updates[pid] =
                    reading.copy(
                        value = targetValue.toInt().toString(),
                        timestamp = System.currentTimeMillis()
                    )
            }
            return
        }

        // Get animation parameters based on sensor type
        val (baseSpeed, maxMultiplier) =
            when (pid) {
                PID_THROTTLE_POSITION -> Pair(2.0, 3.0) // Throttle position changes quickly
                PID_COOLANT_TEMP -> Pair(0.2, 1.5) // Temperature changes very slowly
                PID_INTAKE_AIR_TEMP -> Pair(0.2, 1.5) // Temperature changes very slowly
                PID_FUEL_LEVEL -> Pair(0.1, 1.2) // Fuel level changes extremely slowly
                else -> Pair(1.0, 2.0) // Default values for other sensors
            }

        // Calculate animation speed based on distance
        val speedMultiplier = min(1.0 + (abs(distance) / 20.0), maxMultiplier)
        val speed = baseSpeed * speedMultiplier

        // Calculate new value
        val movement = if (distance > 0) min(speed, distance) else max(-speed, distance)
        val newValue = currentValue + movement

        // Update current value
        currentValues[pid] = newValue

        // Create new reading with animated value
        updates[pid] =
            reading.copy(
                value = newValue.toInt().toString(),
                timestamp = System.currentTimeMillis()
            )
    }

    /** Add sensor reading to history for smoothing and prediction */
    private fun addToHistory(pid: String, reading: SensorReading) {
        // Skip invalid readings
        val valueStr = reading.value
        val value = valueStr.toDoubleOrNull() ?: return

        // Update the maximum observed value if this reading is higher
        updateMaxObservedValue(pid, value)

        // Validate and clamp the value to valid range before storing
        val validatedValue = validateSensorValue(pid, value)
        val timestamp = System.currentTimeMillis()

        // Store the target value for animation
        targetValues[pid] = validatedValue

        when (pid) {
            PID_RPM -> {
                rpmHistory.add(Pair(timestamp, validatedValue))
                if (rpmHistory.size > MAX_HISTORY_SIZE) rpmHistory.removeFirst()
            }

            PID_SPEED -> {
                speedHistory.add(Pair(timestamp, validatedValue))
                if (speedHistory.size > MAX_HISTORY_SIZE) speedHistory.removeFirst()
            }

            PID_THROTTLE_POSITION -> {
                throttleHistory.add(Pair(timestamp, validatedValue))
                if (throttleHistory.size > MAX_HISTORY_SIZE) throttleHistory.removeFirst()
            }

            PID_COOLANT_TEMP -> {
                tempHistory.add(Pair(timestamp, validatedValue))
                if (tempHistory.size > MAX_HISTORY_SIZE) tempHistory.removeFirst()
            }

            PID_ENGINE_LOAD -> {
                loadHistory.add(Pair(timestamp, validatedValue))
                if (loadHistory.size > MAX_HISTORY_SIZE) loadHistory.removeFirst()
            }

            PID_INTAKE_MANIFOLD_PRESSURE -> {
                pressureHistory.add(Pair(timestamp, validatedValue))
                if (pressureHistory.size > MAX_HISTORY_SIZE) pressureHistory.removeFirst()
            }

            PID_FUEL_LEVEL -> {
                fuelHistory.add(Pair(timestamp, validatedValue))
                if (fuelHistory.size > MAX_HISTORY_SIZE) fuelHistory.removeFirst()
            }

            PID_TIMING_ADVANCE -> {
                timingHistory.add(Pair(timestamp, validatedValue))
                if (timingHistory.size > MAX_HISTORY_SIZE) timingHistory.removeFirst()
            }

            PID_MAF_RATE -> {
                airflowHistory.add(Pair(timestamp, validatedValue))
                if (airflowHistory.size > MAX_HISTORY_SIZE) airflowHistory.removeFirst()
            }

            PID_INTAKE_AIR_TEMP -> {
                airTempHistory.add(Pair(timestamp, validatedValue))
                if (airTempHistory.size > MAX_HISTORY_SIZE) airTempHistory.removeFirst()
            }
        }
    }

    /** Sets up error recovery monitoring to restart if issues occur */
    private fun setupRecoveryMonitoring() {
        monitoringRecoveryJob?.cancel()

        monitoringRecoveryJob =
            viewModelScope.launch {
                try {
                    // Wait for sensor readings - if no readings come in for a long period,
                    // restart
                    var lastReadingTime = System.currentTimeMillis()

                    while (state.value.isMonitoring) {
                        delay(5000) // Check every 5 seconds

                        // If the last reading is older than 10 seconds, try to restart
                        // monitoring
                        val currentTime = System.currentTimeMillis()
                        val timeSinceLastReading = currentTime - lastReadingTime

                        // Check for fresh readings to determine if we're still getting data
                        val freshReadings = hasFreshReadings()

                        if (freshReadings) {
                            lastReadingTime = currentTime
                        } else if (timeSinceLastReading > 10000) {
                            // More than 10 seconds without readings - restart monitoring
                            restartMonitoring()
                            lastReadingTime = currentTime
                        }
                    }
                } catch (e: CancellationException) {
                    // Normal during shutdown
                } catch (e: Exception) {
                    // Log but don't crash
                    println("Error in recovery monitoring: ${e.message}")
                }
            }
    }

    /** Sets up error recovery monitoring for selective sensor monitoring */
    private fun setupSelectiveRecoveryMonitoring(selectedSensorIds: Set<String>) {
        monitoringRecoveryJob?.cancel()

        monitoringRecoveryJob =
            viewModelScope.launch {
                try {
                    var lastReadingTime = System.currentTimeMillis()

                    while (state.value.isMonitoring) {
                        delay(5000) // Check every 5 seconds

                        val currentTime = System.currentTimeMillis()
                        val timeSinceLastReading = currentTime - lastReadingTime

                        // Check for fresh readings from selected sensors only
                        val freshReadings = hasFreshReadingsFromSelected(selectedSensorIds)

                        if (freshReadings) {
                            lastReadingTime = currentTime
                        } else if (timeSinceLastReading > 10000) {
                            // More than 10 seconds without readings - restart monitoring
                            restartSelectiveMonitoring(selectedSensorIds)
                            lastReadingTime = currentTime
                        }
                    }
                } catch (e: CancellationException) {
                    // Normal during shutdown
                } catch (e: Exception) {
                    // Log but don't crash
                    println("Error in selective recovery monitoring: ${e.message}")
                }
            }
    }

    /** Check if we have any fresh readings from the critical sensors */
    private fun hasFreshReadings(): Boolean {
        val currentTime = System.currentTimeMillis()
        var hasFresh = false

        // Check RPM reading
        state.value.rpmReading?.let { reading ->
            val timestamp = reading.timestamp
            if (currentTime - timestamp < 5000) {
                hasFresh = true
            }
        }

        // Check speed reading
        if (!hasFresh) {
            state.value.speedReading?.let { reading ->
                val timestamp = reading.timestamp
                if (currentTime - timestamp < 5000) {
                    hasFresh = true
                }
            }
        }

        return hasFresh
    }

    /** Check if we have any fresh readings from the selected sensors */
    private fun hasFreshReadingsFromSelected(selectedSensorIds: Set<String>): Boolean {
        val currentTime = System.currentTimeMillis()

        for (sensorId in selectedSensorIds) {
            val reading = when (sensorId) {
                PID_RPM -> state.value.rpmReading
                PID_SPEED -> state.value.speedReading
                PID_COOLANT_TEMP -> state.value.coolantTempReading
                PID_INTAKE_AIR_TEMP -> state.value.intakeAirTempReading
                PID_THROTTLE_POSITION -> state.value.throttlePositionReading
                PID_FUEL_LEVEL -> state.value.fuelLevelReading
                PID_ENGINE_LOAD -> state.value.engineLoadReading
                PID_INTAKE_MANIFOLD_PRESSURE -> state.value.intakeManifoldPressureReading
                PID_TIMING_ADVANCE -> state.value.timingAdvanceReading
                PID_MAF_RATE -> state.value.massAirFlowReading
                else -> null
            }

            reading?.let {
                if (currentTime - it.timestamp < 5000) {
                    return true
                }
            }
        }

        return false
    }

    /** Restart monitoring if it stops responding */
    private suspend fun restartMonitoring() {
        try {
            // First stop any existing monitoring
            sensorRepository.stopMonitoring()
            delay(500) // Give it time to clean up

            // If we're still supposed to be monitoring, restart it
            if (state.value.isMonitoring) {
                // Group sensors by priority
                val highPrioritySensors = listOf(PID_RPM, PID_SPEED, PID_THROTTLE_POSITION)
                val mediumPrioritySensors =
                    listOf(PID_COOLANT_TEMP, PID_ENGINE_LOAD, PID_INTAKE_MANIFOLD_PRESSURE)
                val lowPrioritySensors =
                    listOf(
                        PID_INTAKE_AIR_TEMP,
                        PID_FUEL_LEVEL,
                        PID_TIMING_ADVANCE,
                        PID_MAF_RATE
                    )

                // Use a fixed refresh rate of 1ms
                val baseRefreshRate = 200L

                // Restart monitoring with the fixed refresh rate
                sensorRepository.startPrioritizedMonitoring(
                    highPrioritySensors,
                    mediumPrioritySensors,
                    lowPrioritySensors,
                    baseRefreshRate
                )
            }
        } catch (e: Exception) {
            println("Error restarting monitoring: ${e.message}")
            // Don't update the state here - we'll try again later
        }
    }

    /** Restart selective monitoring if it stops responding */
    private suspend fun restartSelectiveMonitoring(selectedSensorIds: Set<String>) {
        try {
            // First stop any existing monitoring
            sensorRepository.stopMonitoring()
            delay(500) // Give it time to clean up

            // If we're still supposed to be monitoring, restart it
            if (state.value.isMonitoring) {
                // Group selected sensors by priority
                val allHighPrioritySensors = listOf(PID_RPM, PID_SPEED, PID_THROTTLE_POSITION)
                val allMediumPrioritySensors =
                    listOf(PID_COOLANT_TEMP, PID_ENGINE_LOAD, PID_INTAKE_MANIFOLD_PRESSURE)
                val allLowPrioritySensors =
                    listOf(
                        PID_INTAKE_AIR_TEMP,
                        PID_FUEL_LEVEL,
                        PID_TIMING_ADVANCE,
                        PID_MAF_RATE
                    )

                // Filter by selected sensors only
                val highPrioritySensors = allHighPrioritySensors.filter { selectedSensorIds.contains(it) }
                val mediumPrioritySensors = allMediumPrioritySensors.filter { selectedSensorIds.contains(it) }
                val lowPrioritySensors = allLowPrioritySensors.filter { selectedSensorIds.contains(it) }

                // Use a fixed refresh rate of 200ms
                val baseRefreshRate = 200L

                // Restart monitoring with the selected sensors
                sensorRepository.startPrioritizedMonitoring(
                    highPrioritySensors,
                    mediumPrioritySensors,
                    lowPrioritySensors,
                    baseRefreshRate
                )
            }
        } catch (e: Exception) {
            println("Error restarting selective monitoring: ${e.message}")
            // Don't update the state here - we'll try again later
        }
    }

    /** Handle errors and update state */
    private fun handleError(errorMessage: String) {
        _state.update { it.copy(isLoading = false, error = errorMessage) }
    }

    /** Initial setup - removed automatic monitoring start */
    init {
        // Remove automatic monitoring start - let screens control when to start monitoring
        // startMonitoring()

        // Observe snapshot collector state
        viewModelScope.launch {
            sensorSnapshotCollector.state.collectLatest { collectorState ->
                _state.update { currentState ->
                    currentState.copy(
                        snapshotUploadStatus = when (collectorState.lastUploadStatus) {
                            SensorSnapshotCollector.UploadStatus.NONE -> SnapshotUploadStatus.NONE
                            SensorSnapshotCollector.UploadStatus.SUCCESS -> SnapshotUploadStatus.SUCCESS
                            SensorSnapshotCollector.UploadStatus.ERROR -> SnapshotUploadStatus.ERROR
                        },
                        snapshotUploadError = collectorState.lastUploadError,
                        snapshotCollectionInProgress = collectorState.currentCycleReadingsCount > 0 &&
                                !collectorState.isReadyToUpload,
                        snapshotReadingsCount = collectorState.currentCycleReadingsCount,
                        snapshotLastUploadTimestamp = collectorState.lastSnapshotTimestamp,
                        snapshotLastUploadedReadingsCount = collectorState.lastUploadedReadingsCount
                    )
                }
            }
        }
    }

    /** Stops monitoring sensors when ViewModel is cleared */
    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
        interpolationJob?.cancel()
    }

    // Get a significant jump threshold for each sensor type
    private fun getSignificantJumpThreshold(pid: String): Double {
        return when (pid) {
            PID_RPM -> 150.0 // Reduced threshold for more responsive RPM changes
            PID_SPEED -> 4.0
            PID_THROTTLE_POSITION -> 7.0
            PID_COOLANT_TEMP -> 2.5
            PID_ENGINE_LOAD -> 7.0
            PID_INTAKE_MANIFOLD_PRESSURE -> 8.0
            PID_FUEL_LEVEL -> 2.5
            PID_TIMING_ADVANCE -> 2.5
            PID_MAF_RATE -> 7.0
            PID_INTAKE_AIR_TEMP -> 2.5
            else -> 5.0
        }
    }

    // Get the maximum expected range for a sensor (used for easing calculations)
    private fun getMaximumRange(pid: String): Double {
        return when (pid) {
            PID_RPM -> 8000.0
            PID_SPEED -> 200.0
            PID_THROTTLE_POSITION -> 100.0
            PID_COOLANT_TEMP -> 150.0
            PID_ENGINE_LOAD -> 100.0
            PID_INTAKE_MANIFOLD_PRESSURE -> 255.0
            PID_FUEL_LEVEL -> 100.0
            PID_TIMING_ADVANCE -> 64.0
            PID_MAF_RATE -> 655.35
            PID_INTAKE_AIR_TEMP -> 100.0
            else -> 100.0
        }
    }
}
