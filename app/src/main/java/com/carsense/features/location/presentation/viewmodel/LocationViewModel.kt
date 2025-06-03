package com.carsense.features.location.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.core.room.dao.LocationPointDao
import com.carsense.features.vehicles.data.db.VehicleDao
import com.carsense.core.room.entity.LocationPointEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationPointDao: LocationPointDao,
    private val vehicleDao: VehicleDao
) : ViewModel() {

    // Use a MutableStateFlow to provide initial empty state and update later
    private val _locationPoints = MutableStateFlow<Flow<List<LocationPointEntity>>>(emptyFlow())
    val locationPoints: StateFlow<Flow<List<LocationPointEntity>>> = _locationPoints

    init {
        loadLocationPoints()
    }

    private fun loadLocationPoints() {
        viewModelScope.launch {
            try {
                // Get the latest vehicle
                val latestVehicle = vehicleDao.getLatestVehicle()

                // If we have a valid vehicle ID, get all location points for that vehicle
                if (latestVehicle != null) {
                    val vehicleId = latestVehicle.localId
                    _locationPoints.value = locationPointDao.getLocationsForVehicle(vehicleId)
                    Timber.d("Loading location points for vehicle ID: $vehicleId")
                } else {
                    Timber.w("No vehicles found in database")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading location points")
            }
        }
    }

    // Function to format location point data for display (if needed)
    fun formatCoordinate(value: Double): String {
        return String.format("%.6f", value)
    }
} 