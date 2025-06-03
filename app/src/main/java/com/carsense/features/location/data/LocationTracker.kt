package com.carsense.features.location.data

import android.content.Context
import com.carsense.features.location.data.service.LocationService
import com.carsense.features.vehicles.domain.model.Vehicle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * LocationTracker manages location tracking based on Bluetooth connectivity state.
 * It automatically starts tracking when Bluetooth is connected and stops when disconnected.
 *
 * DEPRECATED: This class is no longer used directly. Location tracking is now handled by
 * ForegroundLocationService which provides better background tracking capabilities.
 * This class is kept for reference purposes only.
 */
@Deprecated("Use ForegroundLocationService instead for location tracking")
@Singleton
class LocationTracker @Inject constructor(
    private val locationService: LocationService,
    @ApplicationContext private val context: Context
) {
    init {
        Timber.w("LocationTracker is instantiated but should not be used directly. Use ForegroundLocationService instead.")
    }

    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private var currentVehicle: Vehicle? = null

    /**
     * Starts location tracking when Bluetooth is connected
     * @param vehicle The current vehicle associated with the tracking session
     */
    fun onBluetoothConnected(vehicle: Vehicle?) {
        Timber.w("LocationTracker.onBluetoothConnected called but this class is deprecated. Use ForegroundLocationService instead.")
        if (_isTracking.value) {
            Timber.d("Location tracking already active, not starting again")
            return
        }

        currentVehicle = vehicle
        Timber.d("Starting location tracking for vehicle: ${vehicle?.make} ${vehicle?.model}")

        trackerScope.launch {
            try {
                _isTracking.value = true
                locationService.requestLocationUpdates(1000) // 1 second interval
                    .collect { location ->
                        // Here you could save the location to a repository or database
                        // For now, we're just logging it
                        Timber.d(
                            "Location update: lat=${location.latitude}, lon=${location.longitude}, " +
                                    "speed=${location.speed} m/s, accuracy=${location.accuracy}m"
                        )
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error in location tracking")
                _isTracking.value = false
            }
        }
    }

    /**
     * Stops location tracking when Bluetooth is disconnected
     */
    fun onBluetoothDisconnected() {
        Timber.w("LocationTracker.onBluetoothDisconnected called but this class is deprecated. Use ForegroundLocationService instead.")
        if (!_isTracking.value) {
            Timber.d("Location tracking not active, nothing to stop")
            return
        }

        Timber.d("Stopping location tracking")
        locationService.stopLocationUpdates()
        _isTracking.value = false
    }

    /**
     * Releases resources used by the tracker
     */
    fun release() {
        Timber.w("LocationTracker.release called but this class is deprecated. Use ForegroundLocationService instead.")
        onBluetoothDisconnected()
        trackerScope.cancel()
    }
} 