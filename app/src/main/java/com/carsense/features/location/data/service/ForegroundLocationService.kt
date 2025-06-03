package com.carsense.features.location.data.service

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
import com.carsense.features.vehicles.data.db.VehicleDao
import com.carsense.features.location.domain.model.LocationPoint
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
import com.carsense.features.location.data.mapper.LocationMapper
import com.carsense.core.room.dao.LocationPointDao

/**
 * A foreground service that tracks location updates and saves them to the database.
 * This service is started when a Bluetooth connection is established and stopped when disconnected.
 */
@AndroidEntryPoint
class ForegroundLocationService : Service() {

    @Inject
    lateinit var fusedLocationClient: FusedLocationProviderClient

    @Inject
    lateinit var vehicleDao: VehicleDao

    @Inject
    lateinit var locationPointDao: LocationPointDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var locationCallback: LocationCallback

    private var currentVehicleLocalId: Long? = null // Will be set via Intent

    companion object {
        const val ACTION_START_LOCATION_SERVICE = "ACTION_START_LOCATION_SERVICE"
        const val ACTION_STOP_LOCATION_SERVICE = "ACTION_STOP_LOCATION_SERVICE"
        const val EXTRA_VEHICLE_LOCAL_ID = "EXTRA_VEHICLE_LOCAL_ID"

        private const val TAG = "ForegroundLocationService"
        private const val NOTIFICATION_CHANNEL_ID = "LocationServiceChannel"
        private const val NOTIFICATION_ID = 12345
        private const val LOCATION_UPDATE_INTERVAL_MS = 2000L // 2 seconds interval
        private const val FASTEST_LOCATION_UPDATE_INTERVAL_MS = 1000L // 1 second minimum interval
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        createNotificationChannel()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("ForegroundLocationService onStartCommand received")
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
                        "ForegroundLocationService: Location update - Lat: ${location.latitude}, " +
                                "Lon: ${location.longitude}, Speed: ${if (location.hasSpeed()) location.speed else "N/A"} m/s"
                    )

                    val vehicleId = currentVehicleLocalId
                    if (vehicleId == null) {
                        Timber.w("ForegroundLocationService: currentVehicleLocalId is null, cannot save location.")
                        return
                    }

                    // Create a location point object
                    val locationPoint = LocationPoint(
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

                    // Save directly to database without filtering
                    val locationEntity = LocationMapper.toEntity(locationPoint)

                    // Save to database
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val insertedId = locationPointDao.insert(locationEntity)
                            Timber.d("Saved location to database with ID: $insertedId")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to save location to database")
                        }
                    }

                    Timber.d("ForegroundLocationService: Location point saved: $locationPoint")
                }
            }

            override fun onLocationAvailability(locationAvailability: LocationAvailability) {
                Timber.d("ForegroundLocationService: Availability changed: ${locationAvailability.isLocationAvailable}")
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
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_LOCATION_UPDATE_INTERVAL_MS)
            setWaitForAccurateLocation(false) // false: prioritize timely updates over perfect accuracy
            setMinUpdateDistanceMeters(0f) // Explicitly set smallest displacement
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
                .addOnSuccessListener {
                    Timber.i("ForegroundLocationService: Successfully registered for location updates.")
                }
                .addOnFailureListener { e ->
                    Timber.e(
                        e,
                        "ForegroundLocationService: Failed to register for location updates."
                    )
                    stopSelf()
                }
        } catch (e: Exception) {
            Timber.e(e, "Exception while requesting location updates.")
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
            .addOnSuccessListener {
                Timber.d("ForegroundLocationService: Successfully unregistered location updates.")
            }
            .addOnFailureListener { e ->
                Timber.e(e, "ForegroundLocationService: Error unregistering location updates.")
            }
    }

    private fun startForegroundService() {
        // Create PendingIntent to launch the app when notification is clicked
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("CarSense Active")
            .setContentText("Tracking your journey")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Location Service"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(notificationChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // This is not a bound service
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        serviceScope.cancel()
        Timber.d("ForegroundLocationService destroyed")
    }
} 