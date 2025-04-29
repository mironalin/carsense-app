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
        val isMonitoring: Boolean = false,
        val refreshRateMs: Long = 1000 // Default refresh rate of 1 second
)

/** ViewModel for the Sensors screen that manages sensor reading states */
@HiltViewModel
class SensorViewModel @Inject constructor(private val sensorRepository: SensorRepository) :
        ViewModel() {

    private val _state = MutableStateFlow(SensorState())
    val state: StateFlow<SensorState> = _state.asStateFlow()

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
                // Start monitoring the RPM sensor (PID 0C) with the specified refresh rate
                sensorRepository.startMonitoringSensors(listOf("0C"), refreshRateMs)

                // Collect the RPM reading
                val rpmFlow = sensorRepository.getSensorReadings("0C")
                viewModelScope.launch {
                    rpmFlow.collect { reading ->
                        // Log the raw value for debugging
                        println("RPM Reading received: ${reading.value} ${reading.unit}")
                        _state.update { it.copy(rpmReading = reading, isLoading = false) }
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
