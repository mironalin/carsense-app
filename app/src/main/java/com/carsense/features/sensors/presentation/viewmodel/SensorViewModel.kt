package com.carsense.features.sensors.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.domain.repository.SensorRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State container for the Sensors screen */
data class SensorState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val rpmReading: SensorReading? = null,
        val speedReading: SensorReading? = null,
        val coolantTempReading: SensorReading? = null,
        val intakeAirTempReading: SensorReading? = null,
        val throttlePositionReading: SensorReading? = null,
        val isMonitoring: Boolean = false,
        val refreshRateMs: Long = 1000 // Default refresh rate of 1 second
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
                // Start monitoring the sensors with the specified refresh rate
                sensorRepository.startMonitoringSensors(
                        listOf(
                                PID_RPM,
                                PID_SPEED,
                                PID_COOLANT_TEMP,
                                PID_INTAKE_AIR_TEMP,
                                PID_THROTTLE_POSITION
                        ),
                        refreshRateMs
                )

                // Collect the RPM readings
                val rpmFlow = sensorRepository.getSensorReadings(PID_RPM)
                viewModelScope.launch {
                    rpmFlow.collect { reading ->
                        // Log the raw value for debugging
                        println("RPM Reading received: ${reading.value} ${reading.unit}")
                        _state.update { it.copy(rpmReading = reading, isLoading = false) }
                    }
                }

                // Collect the speed readings
                val speedFlow = sensorRepository.getSensorReadings(PID_SPEED)
                viewModelScope.launch {
                    speedFlow.collect { reading ->
                        println("Speed Reading received: ${reading.value} ${reading.unit}")
                        _state.update { it.copy(speedReading = reading) }
                    }
                }

                // Collect the coolant temperature readings
                val coolantTempFlow = sensorRepository.getSensorReadings(PID_COOLANT_TEMP)
                viewModelScope.launch {
                    coolantTempFlow.collect { reading ->
                        println(
                                "Coolant Temperature Reading received: ${reading.value} ${reading.unit}"
                        )
                        _state.update { it.copy(coolantTempReading = reading) }
                    }
                }

                // Collect the intake air temperature readings
                val intakeAirTempFlow = sensorRepository.getSensorReadings(PID_INTAKE_AIR_TEMP)
                viewModelScope.launch {
                    intakeAirTempFlow.collect { reading ->
                        println(
                                "Intake Air Temperature Reading received: ${reading.value} ${reading.unit}"
                        )
                        _state.update { it.copy(intakeAirTempReading = reading) }
                    }
                }

                // Collect the throttle position readings
                val throttlePositionFlow = sensorRepository.getSensorReadings(PID_THROTTLE_POSITION)
                viewModelScope.launch {
                    throttlePositionFlow.collect { reading ->
                        println(
                                "Throttle Position Reading received: ${reading.value} ${reading.unit}"
                        )
                        _state.update { it.copy(throttlePositionReading = reading) }
                    }
                }
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
