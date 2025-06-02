package com.carsense.features.welcome.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.core.model.Vehicle
import com.carsense.core.repository.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VehicleSelectionViewModel @Inject constructor(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(VehicleSelectionState())
    val state: StateFlow<VehicleSelectionState> = _state.asStateFlow()

    // Separate scopes for data loading operations to allow safe cancellation
    private var dataLoadingScope = SupervisorJob()

    // Track active jobs to cancel them individually
    private var vehiclesJob: Job? = null
    private var selectedVehicleJob: Job? = null

    init {
        // Initial data loading
        resumeDataLoading()
    }

    fun pauseDataLoading() {
        Timber.d("VehicleSelectionViewModel: Pausing data loading")

        // Cancel existing jobs safely
        vehiclesJob?.cancel("Navigation transition")
        vehiclesJob = null

        selectedVehicleJob?.cancel("Navigation transition")
        selectedVehicleJob = null

        // Cancel the entire scope
        dataLoadingScope.cancel("Navigation transition")

        // Create a new scope for future use
        dataLoadingScope = SupervisorJob()
    }

    fun resumeDataLoading() {
        Timber.d("VehicleSelectionViewModel: Resuming data loading")

        // Only start new jobs if they're not already running
        if (vehiclesJob == null) {
            loadVehicles()
        }

        if (selectedVehicleJob == null) {
            checkSelectedVehicle()
        }
    }

    fun onEvent(event: VehicleSelectionEvent) {
        when (event) {
            is VehicleSelectionEvent.RefreshVehicles -> {
                refreshVehicles()
            }

            is VehicleSelectionEvent.SelectVehicle -> {
                selectVehicle(event.uuid)
            }

            is VehicleSelectionEvent.AddNewVehicle -> {
                addNewVehicle(event.vehicle)
            }

            is VehicleSelectionEvent.DeleteVehicle -> {
                deleteVehicle(event.uuid)
            }
        }
    }

    private fun loadVehicles() {
        // Cancel any existing job first
        vehiclesJob?.cancel()

        // Create a new job with error handling
        vehiclesJob = viewModelScope.launch(dataLoadingScope) {
            try {
                // Only show loading if we don't have vehicles already
                val showLoading = _state.value.vehicles.isEmpty()
                _state.update { it.copy(isLoading = showLoading, error = null) }

                vehicleRepository.getAllVehicles().catch { e ->
                    if (e is CancellationException) {
                        // Log but don't update state for cancellation exceptions
                        Timber.d("Vehicle loading job was cancelled normally")
                    } else {
                        Timber.e(e, "Error loading vehicles")
                        _state.update {
                            it.copy(
                                isLoading = false, error = "Failed to load vehicles: ${e.message}"
                            )
                        }
                    }
                }.collect { vehicles ->
                    _state.update {
                        it.copy(
                            vehicles = vehicles,
                            isLoading = false,
                            selectedVehicle = vehicles.find { v -> v.isSelected })
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    // Log but don't update state for cancellation exceptions
                    Timber.d("Vehicle loading job was cancelled normally")
                } else {
                    Timber.e(e, "Error loading vehicles")
                    _state.update {
                        it.copy(
                            isLoading = false, error = "Failed to load vehicles: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun checkSelectedVehicle() {
        // Cancel any existing job first
        selectedVehicleJob?.cancel()

        // Create a new job with error handling
        selectedVehicleJob = viewModelScope.launch(dataLoadingScope) {
            try {
                vehicleRepository.isVehicleSelected().catch { e ->
                    if (e !is CancellationException) {
                        Timber.e(e, "Error checking selected vehicle")
                    }
                }.collect { isSelected ->
                    _state.update { it.copy(isVehicleSelected = isSelected) }
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Timber.e(e, "Error checking selected vehicle")
                }
            }
        }
    }

    private fun refreshVehicles() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }

                val result = vehicleRepository.refreshVehicles()

                // Important: Always set isLoading to false when refresh completes
                result.onSuccess {
                    _state.update { it.copy(isLoading = false) }
                }.onFailure { e ->
                    if (e is CancellationException) {
                        Timber.d("Refresh vehicles job was cancelled normally")
                    } else {
                        Timber.e(e, "Error refreshing vehicles")
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = "Failed to refresh vehicles: ${e.message}"
                            )
                        }
                    }
                }
                // The vehicles will be updated through the Flow collected in loadVehicles()

            } catch (e: Exception) {
                if (e is CancellationException) {
                    Timber.d("Refresh vehicles job was cancelled normally")
                } else {
                    Timber.e(e, "Error refreshing vehicles")
                    _state.update {
                        it.copy(
                            isLoading = false, error = "Failed to refresh vehicles: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun selectVehicle(uuid: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isSelecting = true, error = null) }

                val result = vehicleRepository.selectVehicle(uuid)
                result.onSuccess {
                    _state.update { it.copy(isSelecting = false) }
                }.onFailure { e ->
                    if (e is CancellationException) {
                        Timber.d("Select vehicle job was cancelled normally")
                    } else {
                        Timber.e(e, "Error selecting vehicle")
                        _state.update {
                            it.copy(
                                isSelecting = false,
                                error = "Failed to select vehicle: ${e.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Timber.d("Select vehicle job was cancelled normally")
                } else {
                    Timber.e(e, "Error selecting vehicle")
                    _state.update {
                        it.copy(
                            isSelecting = false, error = "Failed to select vehicle: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun addNewVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isAddingVehicle = true, error = null) }

                val result = vehicleRepository.createVehicle(vehicle)
                result.onSuccess { newVehicle ->
                    _state.update { it.copy(isAddingVehicle = false) }
                    // Select the newly added vehicle
                    selectVehicle(newVehicle.uuid)
                }.onFailure { e ->
                    if (e is CancellationException) {
                        Timber.d("Add vehicle job was cancelled normally")
                    } else {
                        Timber.e(e, "Error adding vehicle")
                        _state.update {
                            it.copy(
                                isAddingVehicle = false,
                                error = "Failed to add vehicle: ${e.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Timber.d("Add vehicle job was cancelled normally")
                } else {
                    Timber.e(e, "Error adding vehicle")
                    _state.update {
                        it.copy(
                            isAddingVehicle = false, error = "Failed to add vehicle: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun deleteVehicle(uuid: String) {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isDeletingVehicle = true, error = null) }

                val result = vehicleRepository.deleteVehicle(uuid)
                result.onSuccess {
                    _state.update { it.copy(isDeletingVehicle = false) }
                }.onFailure { e ->
                    if (e is CancellationException) {
                        Timber.d("Delete vehicle job was cancelled normally")
                    } else {
                        Timber.e(e, "Error deleting vehicle")
                        _state.update {
                            it.copy(
                                isDeletingVehicle = false,
                                error = "Failed to delete vehicle: ${e.message}"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) {
                    Timber.d("Delete vehicle job was cancelled normally")
                } else {
                    Timber.e(e, "Error deleting vehicle")
                    _state.update {
                        it.copy(
                            isDeletingVehicle = false,
                            error = "Failed to delete vehicle: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up all jobs when ViewModel is cleared
        pauseDataLoading()
    }
}

data class VehicleSelectionState(
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val isVehicleSelected: Boolean = false,
    val isLoading: Boolean = false,
    val isSelecting: Boolean = false,
    val isAddingVehicle: Boolean = false,
    val isDeletingVehicle: Boolean = false,
    val error: String? = null
)

sealed class VehicleSelectionEvent {
    data object RefreshVehicles : VehicleSelectionEvent()
    data class SelectVehicle(val uuid: String) : VehicleSelectionEvent()
    data class AddNewVehicle(val vehicle: Vehicle) : VehicleSelectionEvent()
    data class DeleteVehicle(val uuid: String) : VehicleSelectionEvent()
} 