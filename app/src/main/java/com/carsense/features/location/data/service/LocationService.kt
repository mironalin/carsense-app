package com.carsense.features.location.data.service

import android.annotation.SuppressLint
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume

interface LocationService {
    fun requestLocationUpdates(
        intervalMillis: Long = LocationService.DEFAULT_UPDATE_INTERVAL
    ): Flow<Location>

    suspend fun getLastKnownLocation(): Location?
    fun stopLocationUpdates()

    companion object {
        const val DEFAULT_UPDATE_INTERVAL = 5000L // 5 seconds
        const val FASTEST_UPDATE_INTERVAL = 2000L // 2 seconds, if available sooner
    }
}

class AndroidLocationService
@Inject
constructor(
    // No @ApplicationContext needed here if FusedLocationProviderClient is provided by Hilt
    // with it
    private val fusedLocationProviderClient: FusedLocationProviderClient
) : LocationService {

    private var locationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission") // Permissions are checked before calling
    override fun requestLocationUpdates(intervalMillis: Long): Flow<Location> = callbackFlow {
        if (locationCallback != null) {
            Timber.w(
                "Location updates were already requested. Closing new request. Stop previous updates first."
            )
            close(
                IllegalStateException(
                    "Location updates already requested. Stop previous updates first."
                )
            )
            return@callbackFlow
        }

        val request =
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                .setMinUpdateIntervalMillis(LocationService.FASTEST_UPDATE_INTERVAL)
                .build()

        locationCallback =
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let {
                        Timber.d(
                            "New location: lat=${it.latitude}, lon=${it.longitude}, speed=${it.speed} m/s, accuracy=${it.accuracy}m"
                        )
                        trySend(it).isSuccess // Offer the location to the flow
                    }
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    Timber.d("Location availability: ${availability.isLocationAvailable}")
                    if (!availability.isLocationAvailable) {
                        // Optionally, emit an error or a specific state
                        // e.g., channel.trySend(Result.failure(Exception("Location not
                        // available"))).isSuccess
                    }
                }
            }

        Timber.d("Requesting location updates with interval: $intervalMillis ms")
        fusedLocationProviderClient.requestLocationUpdates(
            request,
            locationCallback!!,
            Looper.getMainLooper() /* Looper must be prepared for this thread if not main */
        )
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to request location updates")
                close(e) // Close the flow with an exception
            }

        awaitClose { // Called when the Flow is cancelled or closed by consumer
            Timber.d("Stopping location updates (flow closed or explicitly stopped).")
            stopLocationUpdatesInternal()
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastKnownLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedLocationProviderClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Timber.d(
                            "Last known location: lat=${location.latitude}, lon=${location.longitude}, speed=${location.speed} m/s, accuracy=${location.accuracy}m"
                        )
                    } else {
                        Timber.d("Last known location is null.")
                    }
                    continuation.resume(location)
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "Failed to get last known location")
                    continuation.resume(null) // Resume with null on failure
                }
        }

    override fun stopLocationUpdates() {
        Timber.d("Explicitly stopping location updates requested by client.")
        stopLocationUpdatesInternal()
        // Note: If the flow is still active, awaitClose will also call stopLocationUpdatesInternal.
        // This explicit call ensures cleanup even if the flow was never collected or cancelled by
        // consumer.
    }

    private fun stopLocationUpdatesInternal() {
        locationCallback?.let {
            Timber.d("Removing location callback from FusedLocationProviderClient.")
            fusedLocationProviderClient.removeLocationUpdates(it)
            locationCallback = null // Clear the callback to allow new requests
        }
            ?: Timber.d(
                "stopLocationUpdatesInternal called but locationCallback was already null."
            )
    }
}
