package com.carsense.features.sensors.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.domain.repository.SensorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val refreshRateMs: Long =
        800 // Using more conservative refresh rate that works with slower adapters
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
        const val LOW_PRIORITY_FACTOR = 3.0 // Low priority sensors refresh at 3x base rate

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
    }

    /** Coroutine job for monitoring recovery */
    private var monitoringRecoveryJob: Job? = null

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
        val sensorFlows = mapOf(
            PID_RPM to sensorRepository.getSensorReadings(PID_RPM),
            PID_SPEED to sensorRepository.getSensorReadings(PID_SPEED),
            PID_COOLANT_TEMP to sensorRepository.getSensorReadings(PID_COOLANT_TEMP),
            PID_INTAKE_AIR_TEMP to sensorRepository.getSensorReadings(PID_INTAKE_AIR_TEMP),
            PID_THROTTLE_POSITION to sensorRepository.getSensorReadings(PID_THROTTLE_POSITION),
            PID_FUEL_LEVEL to sensorRepository.getSensorReadings(PID_FUEL_LEVEL),
            PID_ENGINE_LOAD to sensorRepository.getSensorReadings(PID_ENGINE_LOAD),
            PID_INTAKE_MANIFOLD_PRESSURE to sensorRepository.getSensorReadings(
                PID_INTAKE_MANIFOLD_PRESSURE
            ),
            PID_TIMING_ADVANCE to sensorRepository.getSensorReadings(PID_TIMING_ADVANCE),
            PID_MAF_RATE to sensorRepository.getSensorReadings(PID_MAF_RATE)
        )

        // Collect from each flow and update state
        sensorFlows.forEach { (pid, flow) ->
            viewModelScope.launch {
                flow.collect { reading -> updateReadingState(pid, reading) }
            }
        }
    }

    /** Update state based on the sensor PID */
    private fun updateReadingState(pid: String, reading: SensorReading) {
        _state.update { state ->
            when (pid) {
                PID_RPM -> state.copy(rpmReading = reading, isLoading = false)
                PID_SPEED -> state.copy(speedReading = reading)
                PID_COOLANT_TEMP -> state.copy(coolantTempReading = reading)
                PID_INTAKE_AIR_TEMP -> state.copy(intakeAirTempReading = reading)
                PID_THROTTLE_POSITION -> state.copy(throttlePositionReading = reading)
                PID_FUEL_LEVEL -> state.copy(fuelLevelReading = reading)
                PID_ENGINE_LOAD -> state.copy(engineLoadReading = reading)
                PID_INTAKE_MANIFOLD_PRESSURE -> state.copy(intakeManifoldPressureReading = reading)
                PID_TIMING_ADVANCE -> state.copy(timingAdvanceReading = reading)
                PID_MAF_RATE -> state.copy(massAirFlowReading = reading)
                else -> state
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
    }
}
