package com.carsense.features.sensors.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.domain.repository.SensorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.LinkedList
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
        val refreshRateMs: Long = 550 // Slightly faster than 600ms but still stable
)

/** ViewModel for the Sensors screen that manages sensor reading states */
@HiltViewModel
class SensorViewModel @Inject constructor(private val sensorRepository: SensorRepository) :
        ViewModel() {

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
    private val MAX_HISTORY_SIZE = 5

    /** Coroutine job for monitoring recovery */
    private var monitoringRecoveryJob: Job? = null

    /** Coroutine job for providing intermediate updates */
    private var interpolationJob: Job? = null

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

    /** Stops monitoring for sensor data */
    fun stopMonitoring() {
        if (!state.value.isMonitoring) {
            return
        }

        monitoringRecoveryJob?.cancel()
        monitoringRecoveryJob = null

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

                // Set up monitoring with prioritization
                sensorRepository.startPrioritizedMonitoring(
                        highPrioritySensors,
                        mediumPrioritySensors,
                        lowPrioritySensors,
                        state.value.refreshRateMs
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

    /** Set up flows for all sensors to collect readings and update UI state */
    private fun setupSensorFlows() {
        // Create a map of all sensor flows for cleaner organization
        val sensorFlows =
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

        // Collect from each flow and update state
        sensorFlows.forEach { (pid, flow) ->
            viewModelScope.launch { flow.collect { reading -> updateReadingState(pid, reading) } }
        }
    }

    /** Update state based on the sensor PID */
    private fun updateReadingState(pid: String, reading: SensorReading) {
        // Add to history for smoothing/prediction (for key sensors)
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

        // Clamp value to valid range
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
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
        return if (Math.abs(smoothedValue - validValue) > getMaxSmoothingDelta(pid)) {
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

        // Apply exponential weighting that favors more recent values
        var totalWeight = 0.0
        var weightedSum = 0.0

        history.forEachIndexed { index, (_, value) ->
            val weight = Math.pow(1.5, index.toDouble())
            weightedSum += value * weight
            totalWeight += weight
        }

        return (weightedSum / totalWeight)
    }

    /**
     * Gets the maximum allowed difference for smoothing Prevents smoothing from hiding genuine
     * large changes
     */
    private fun getMaxSmoothingDelta(pid: String): Double {
        return when (pid) {
            PID_RPM -> 300.0 // 300 RPM max smoothing
            PID_SPEED -> 5.0 // 5 km/h max smoothing
            PID_THROTTLE_POSITION -> 10.0 // 10% max smoothing
            PID_COOLANT_TEMP -> 5.0 // 5°C max smoothing
            PID_INTAKE_AIR_TEMP -> 5.0 // 5°C max smoothing
            PID_ENGINE_LOAD -> 10.0 // 10% max smoothing
            PID_FUEL_LEVEL -> 5.0 // 5% max smoothing
            PID_INTAKE_MANIFOLD_PRESSURE -> 10.0 // 10 kPa max smoothing
            PID_TIMING_ADVANCE -> 5.0 // 5 degrees max smoothing
            PID_MAF_RATE -> 10.0 // 10 g/s max smoothing
            else -> 10.0
        }
    }

    /**
     * Starts the interpolation job that provides intermediate updates to UI for smoother
     * transitions between actual sensor readings
     */
    private fun startInterpolationJob() {
        interpolationJob?.cancel()

        interpolationJob =
                viewModelScope.launch {
                    while (isActive && state.value.isMonitoring) {
                        // Only run interpolation at a faster rate than actual updates
                        delay(150)

                        // Apply predictions to all sensors with sufficient history
                        if (rpmHistory.size >= 2) {
                            predictAndUpdateRPM()
                        }

                        if (speedHistory.size >= 2) {
                            predictAndUpdateSpeed()
                        }

                        if (throttleHistory.size >= 2) {
                            predictAndUpdateSensor(
                                    PID_THROTTLE_POSITION,
                                    throttleHistory,
                                    10.0,
                                    0.02
                            )
                        }

                        if (tempHistory.size >= 2) {
                            predictAndUpdateSensor(PID_COOLANT_TEMP, tempHistory, 5.0, 0.005)
                        }

                        if (loadHistory.size >= 2) {
                            predictAndUpdateSensor(PID_ENGINE_LOAD, loadHistory, 10.0, 0.02)
                        }

                        // Only predict other sensors every 300ms to save resources
                        if (System.currentTimeMillis() % 300 < 150) {
                            if (pressureHistory.size >= 2) {
                                predictAndUpdateSensor(
                                        PID_INTAKE_MANIFOLD_PRESSURE,
                                        pressureHistory,
                                        10.0,
                                        0.02
                                )
                            }

                            if (fuelHistory.size >= 2) {
                                predictAndUpdateSensor(PID_FUEL_LEVEL, fuelHistory, 5.0, 0.005)
                            }

                            if (timingHistory.size >= 2) {
                                predictAndUpdateSensor(PID_TIMING_ADVANCE, timingHistory, 5.0, 0.01)
                            }

                            if (airflowHistory.size >= 2) {
                                predictAndUpdateSensor(PID_MAF_RATE, airflowHistory, 8.0, 0.02)
                            }

                            if (airTempHistory.size >= 2) {
                                predictAndUpdateSensor(
                                        PID_INTAKE_AIR_TEMP,
                                        airTempHistory,
                                        5.0,
                                        0.005
                                )
                            }
                        }
                    }
                }
    }

    /** Generic sensor prediction function */
    private fun predictAndUpdateSensor(
            pid: String,
            history: LinkedList<Pair<Long, Double>>,
            maxChange: Double,
            maxSlope: Double
    ) {
        val currentState = _state.value
        val lastReading =
                when (pid) {
                    PID_THROTTLE_POSITION -> currentState.throttlePositionReading
                    PID_COOLANT_TEMP -> currentState.coolantTempReading
                    PID_ENGINE_LOAD -> currentState.engineLoadReading
                    PID_INTAKE_MANIFOLD_PRESSURE -> currentState.intakeManifoldPressureReading
                    PID_FUEL_LEVEL -> currentState.fuelLevelReading
                    PID_TIMING_ADVANCE -> currentState.timingAdvanceReading
                    PID_MAF_RATE -> currentState.massAirFlowReading
                    PID_INTAKE_AIR_TEMP -> currentState.intakeAirTempReading
                    else -> return
                }
                        ?: return

        // Don't predict if reading is too old
        if (System.currentTimeMillis() - lastReading.timestamp > 2000) return

        try {
            // Get the two most recent readings for trend calculation
            val newest = history.lastOrNull() ?: return
            val previous = history[history.size - 2]

            // Calculate a simple linear trend
            val (prevTime, prevValue) = previous
            val (newestTime, newestValue) = newest

            val timeDiff = newestTime - prevTime
            if (timeDiff <= 0) return

            val valueDiff = newestValue - prevValue
            val slope = valueDiff / timeDiff

            // Don't predict if the change rate is too high (might be erratic)
            if (Math.abs(slope) > maxSlope) return

            // Calculate prediction
            val now = System.currentTimeMillis()
            val timeElapsed = now - newestTime
            val predictedChange = slope * timeElapsed

            // Don't apply huge predictions
            if (Math.abs(predictedChange) > maxChange) return

            val predictedValue = validateSensorValue(pid, newestValue + predictedChange)

            // Update the UI with predicted value, keeping other fields intact
            val predictedReading =
                    lastReading.copy(value = predictedValue.toInt().toString(), timestamp = now)

            _state.update {
                when (pid) {
                    PID_THROTTLE_POSITION -> it.copy(throttlePositionReading = predictedReading)
                    PID_COOLANT_TEMP -> it.copy(coolantTempReading = predictedReading)
                    PID_ENGINE_LOAD -> it.copy(engineLoadReading = predictedReading)
                    PID_INTAKE_MANIFOLD_PRESSURE ->
                            it.copy(intakeManifoldPressureReading = predictedReading)
                    PID_FUEL_LEVEL -> it.copy(fuelLevelReading = predictedReading)
                    PID_TIMING_ADVANCE -> it.copy(timingAdvanceReading = predictedReading)
                    PID_MAF_RATE -> it.copy(massAirFlowReading = predictedReading)
                    PID_INTAKE_AIR_TEMP -> it.copy(intakeAirTempReading = predictedReading)
                    else -> it
                }
            }
        } catch (e: Exception) {
            // Ignore prediction errors, they're not critical
        }
    }

    /** Uses linear interpolation to predict the next RPM value */
    private fun predictAndUpdateRPM() {
        val currentState = _state.value
        val lastReading = currentState.rpmReading ?: return

        // Don't predict if reading is too old
        if (System.currentTimeMillis() - lastReading.timestamp > 2000) return

        try {
            // Get the two most recent readings for trend calculation
            val newest = rpmHistory.lastOrNull() ?: return
            val previous = rpmHistory[rpmHistory.size - 2]

            // Calculate a simple linear trend
            val (prevTime, prevValue) = previous
            val (newestTime, newestValue) = newest

            val timeDiff = newestTime - prevTime
            if (timeDiff <= 0) return

            val valueDiff = newestValue - prevValue
            val slope = valueDiff / timeDiff

            // Don't predict if the change rate is too high (might be erratic)
            if (Math.abs(slope) > 50) return // More than 50 RPM per ms is unrealistic

            // Calculate prediction
            val now = System.currentTimeMillis()
            val timeElapsed = now - newestTime
            val predictedChange = slope * timeElapsed

            // Don't apply huge predictions
            if (Math.abs(predictedChange) > 500) return

            val predictedValue = (newestValue + predictedChange).toInt()

            // Update the UI with predicted value, keeping other fields intact
            val predictedReading =
                    lastReading.copy(value = predictedValue.toString(), timestamp = now)

            _state.update { it.copy(rpmReading = predictedReading) }
        } catch (e: Exception) {
            // Ignore prediction errors, they're not critical
        }
    }

    /** Uses linear interpolation to predict the next Speed value */
    private fun predictAndUpdateSpeed() {
        val currentState = _state.value
        val lastReading = currentState.speedReading ?: return

        // Don't predict if reading is too old
        if (System.currentTimeMillis() - lastReading.timestamp > 2000) return

        try {
            // Get the two most recent readings for trend calculation
            val newest = speedHistory.lastOrNull() ?: return
            val previous = speedHistory[speedHistory.size - 2]

            // Calculate a simple linear trend
            val (prevTime, prevValue) = previous
            val (newestTime, newestValue) = newest

            val timeDiff = newestTime - prevTime
            if (timeDiff <= 0) return

            val valueDiff = newestValue - prevValue
            val slope = valueDiff / timeDiff

            // Don't predict if the change rate is too high (might be erratic)
            if (Math.abs(slope) > 0.05) return // More than 0.05 km/h per ms is unrealistic

            // Calculate prediction
            val now = System.currentTimeMillis()
            val timeElapsed = now - newestTime
            val predictedChange = slope * timeElapsed

            // Don't apply huge predictions
            if (Math.abs(predictedChange) > 10) return

            val predictedValue = (newestValue + predictedChange).toInt()

            // Update the UI with predicted value, keeping other fields intact
            val predictedReading =
                    lastReading.copy(value = predictedValue.toString(), timestamp = now)

            _state.update { it.copy(speedReading = predictedReading) }
        } catch (e: Exception) {
            // Ignore prediction errors, they're not critical
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

                // Restart with a slightly longer refresh rate to be more conservative
                val currentRefreshRate = state.value.refreshRateMs
                val newRefreshRate =
                        if (currentRefreshRate < 1000) currentRefreshRate + 100
                        else currentRefreshRate

                // Update the state with the new refresh rate
                _state.update { it.copy(refreshRateMs = newRefreshRate) }

                // Restart monitoring with the new refresh rate
                sensorRepository.startPrioritizedMonitoring(
                        highPrioritySensors,
                        mediumPrioritySensors,
                        lowPrioritySensors,
                        newRefreshRate
                )
            }
        } catch (e: Exception) {
            println("Error restarting monitoring: ${e.message}")
            // Don't update the state here - we'll try again later
        }
    }

    /** Handle errors and update state */
    private fun handleError(errorMessage: String) {
        _state.update { it.copy(isLoading = false, error = errorMessage) }
    }

    /** Sets a new refresh rate and restarts monitoring if active */
    fun setRefreshRate(refreshRateMs: Long) {
        if (refreshRateMs == state.value.refreshRateMs) return

        val wasMonitoring = state.value.isMonitoring
        if (wasMonitoring) {
            stopMonitoring()
        }

        _state.update { it.copy(refreshRateMs = refreshRateMs) }

        if (wasMonitoring) {
            startMonitoring()
        }
    }

    /** Initial load of sensor readings */
    init {
        startMonitoring()
    }

    /** Stops monitoring sensors when ViewModel is cleared */
    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
        interpolationJob?.cancel()
    }
}
