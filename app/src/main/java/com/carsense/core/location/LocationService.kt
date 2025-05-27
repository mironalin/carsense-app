package com.carsense.core.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.carsense.R
import com.carsense.core.room.dao.LocationPointDao
import com.carsense.core.room.entity.LocationPointEntity
import com.carsense.ui.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class LocationService : Service() {

    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient

    @Inject
    lateinit var locationPointDao: LocationPointDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var locationCallback: LocationCallback

    private var currentVehicleLocalId: Long? = null // Will be set via Intent

    companion object {
        const val ACTION_START_LOCATION_SERVICE = "ACTION_START_LOCATION_SERVICE"
        const val ACTION_STOP_LOCATION_SERVICE = "ACTION_STOP_LOCATION_SERVICE"
        const val EXTRA_VEHICLE_LOCAL_ID = "EXTRA_VEHICLE_LOCAL_ID"

        private const val TAG = "LocationService"
        private const val NOTIFICATION_CHANNEL_ID = "LocationServiceChannel"
        private const val NOTIFICATION_ID = 12345
        private const val LOCATION_UPDATE_INTERVAL_MS = 5000L
        private const val FASTEST_LOCATION_UPDATE_INTERVAL_MS = 5000L
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("LocationService onStartCommand received")
        intent?.let {
            when (it.action) {
                ACTION_START_LOCATION_SERVICE -> {
                    if (it.hasExtra(EXTRA_VEHICLE_LOCAL_ID)) {
                        currentVehicleLocalId = it.getLongExtra(EXTRA_VEHICLE_LOCAL_ID, -1L)
                        if (currentVehicleLocalId == -1L) {
                            Timber.e("Invalid vehicleLocalId received. Stopping service.")
                            stopSelf()
                            return START_NOT_STICKY
                        }
                        Timber.d("Starting location updates for vehicleLocalId: $currentVehicleLocalId")
                        startForegroundService()
                        startLocationUpdates()
                    } else {
                        Timber.e("vehicleLocalId not provided in start intent. Stopping service.")
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }

                ACTION_STOP_LOCATION_SERVICE -> {
                    Timber.d("Stopping location service via action.")
                    stopLocationUpdates()
                    stopForeground(true)
                    stopSelf()
                }

                else -> Timber.w("Unknown action received: ${it.action}")
            }
        }
        return START_STICKY
    }


    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Timber.i(
                        "LocationService: New raw location received - Lat: ${location.latitude}, Lon: ${location.longitude}, Alt: ${if (location.hasAltitude()) location.altitude else "N/A"}, Speed: ${if (location.hasSpeed()) location.speed else "N/A"} m/s, Accuracy: ${if (location.hasAccuracy()) location.accuracy else "N/A"} m"
                    )
                    val vehicleId = currentVehicleLocalId
                    if (vehicleId == null) {
                        Timber.w("LocationService: currentVehicleLocalId is null, cannot save location.")
                        return
                    }

                    val locationPoint = LocationPointEntity(
                        uuid = UUID.randomUUID().toString(),
                        vehicleLocalId = vehicleId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        altitude = if (location.hasAltitude()) location.altitude else null,
                        speed = if (location.hasSpeed()) location.speed else null,
                        accuracy = if (location.hasAccuracy()) location.accuracy else null,
                        timestamp = System.currentTimeMillis(),
                        isSynced = false
                    )
                    Timber.d("LocationService: Prepared LocationPointEntity: $locationPoint")

                    serviceScope.launch {
                        try {
                            val newRowId = locationPointDao.insert(locationPoint)
                            if (newRowId > 0) {
                                Timber.i("LocationService: Location point saved to DB. Row ID: $newRowId, UUID: ${locationPoint.uuid}, VehicleLocalID: $vehicleId")
                            } else {
                                Timber.w("LocationService: Failed to save location point to DB (insert returned no positive rowId). UUID: ${locationPoint.uuid}")
                            }
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "LocationService: Error saving location point to DB. UUID: ${locationPoint.uuid}"
                            )
                        }
                    }
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                Timber.d("LocationService: Availability changed. IsLocationAvailable: ${locationAvailability.isLocationAvailable}, Full details: $locationAvailability")
            }
        }
    }

    private fun startLocationUpdates() {
        // Basic permission check - proper handling should be done before starting service
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Timber.e("Fine location permission not granted. Cannot start updates.")
            // Consider sending a broadcast or updating UI about permission issue
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LOCATION_UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL_MS)
            setWaitForAccurateLocation(false) // false: prioritize timely updates over perfect accuracy
            setMinUpdateDistanceMeters(0f) // Explicitly set smallest displacement
        }.build()

        Timber.d("LocationService: Attempting to request location updates with request: $locationRequest")

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
                .addOnSuccessListener {
                    Timber.i("LocationService: Successfully registered for location updates.")
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "LocationService: Failed to register for location updates.")
                    // Consider stopping the service or retrying if registration fails.
                    // For now, already have stopSelf() in broader catch block.
                }
            // Timber.d("Location updates started.") // Covered by addOnSuccessListener now
        } catch (secEx: SecurityException) {
            Timber.e(
                secEx,
                "SecurityException while requesting location updates. Ensure permissions are granted."
            )
            stopSelf() // Stop service if permission issue occurs at this stage
        } catch (ex: Exception) {
            Timber.e(ex, "Exception while requesting location updates.")
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        Timber.d("Stopping location updates.")
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startForegroundService() {
        val notificationIntent =
            Intent(this, MainActivity::class.java) //  Ensure MainActivity is correctly referenced
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("CarSense Location Service")
            .setContentText("Tracking location for your trips.")
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your app's icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Timber.d("LocationService started in foreground.")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "CarSense Location Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            serviceChannel.description = "Channel for CarSense location tracking service"
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            Timber.d("Notification channel created.")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("LocationService onDestroy.")
        stopLocationUpdates()
        serviceScope.coroutineContext.cancel() // Ensure cancel is resolved
    }
}