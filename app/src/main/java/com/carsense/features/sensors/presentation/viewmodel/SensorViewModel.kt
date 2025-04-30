package com.carsense.features.sensors.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.domain.repository.SensorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
        600 // Using 600ms refresh rate for faster updates with good reliability
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
    }

    /** Starts monitoring sensor readings with configurable refresh rate */
    fun startReadings(refreshRateMs: Long = _state.value.refreshRateMs) {
        if (_state.value.isMonitoring) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    isMonitoring = true,
                    refreshRateMs = refreshRateMs
                )
            }

            try {
                // Prioritize sensors into two groups - high priority and normal priority
                // High priority sensors (RPM, Speed) should be updated more frequently
                val highPrioritySensors = listOf(PID_RPM, PID_SPEED, PID_THROTTLE_POSITION)
                val normalPrioritySensors =
                    listOf(
                        PID_COOLANT_TEMP,
                        PID_INTAKE_AIR_TEMP,
                        PID_FUEL_LEVEL,
                        PID_ENGINE_LOAD,
                        PID_INTAKE_MANIFOLD_PRESSURE,
                        PID_TIMING_ADVANCE,
                        PID_MAF_RATE
                    )

                // Combine all sensors for monitoring
                val allSensors = highPrioritySensors + normalPrioritySensors

                // Start monitoring all sensors at the specified refresh rate
                sensorRepository.startMonitoringSensors(allSensors, refreshRateMs)

                // Set up flows for all sensors
                setupSensorFlows()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to start sensor readings: ${e.message}",
                        isMonitoring = false
                    )
                }
            }
        }
    }

    /** Set up flows for all sensors to collect readings */
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

    /** Sets a new refresh rate and restarts monitoring if active */
    fun setRefreshRate(refreshRateMs: Long) {
        if (refreshRateMs == _state.value.refreshRateMs) return

        val wasMonitoring = _state.value.isMonitoring
        if (wasMonitoring) {
            stopReadings()
        }

        _state.update { it.copy(refreshRateMs = refreshRateMs) }

        if (wasMonitoring) {
            startReadings(refreshRateMs)
        }
    }

    /** Stops monitoring sensor readings */
    fun stopReadings() {
        if (!_state.value.isMonitoring) return

        viewModelScope.launch {
            try {
                sensorRepository.stopMonitoring()
                _state.update { it.copy(isMonitoring = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Failed to stop sensor readings: ${e.message}") }
            }
        }
    }

    /** Refreshes sensor readings */
    fun refreshSensors() {
        stopReadings()
        startReadings()
    }

    /** Initial load of sensor readings */
    init {
        startReadings()
    }

    /** Stops monitoring sensors when ViewModel is cleared */
    override fun onCleared() {
        viewModelScope.launch { sensorRepository.stopMonitoring() }
        super.onCleared()
    }
}
