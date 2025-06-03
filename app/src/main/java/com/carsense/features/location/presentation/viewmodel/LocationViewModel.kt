package com.carsense.features.location.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsense.core.room.dao.LocationPointDao
import com.carsense.features.vehicles.data.db.VehicleDao
import com.carsense.core.room.entity.LocationPointEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationPointDao: LocationPointDao,
    private val vehicleDao: VehicleDao,
    @ApplicationContext val viewModelContext: Context
) : ViewModel() {

    // Use a MutableStateFlow to provide initial empty state and update later
    private val _locationPoints = MutableStateFlow<Flow<List<LocationPointEntity>>>(emptyFlow())
    val locationPoints: StateFlow<Flow<List<LocationPointEntity>>> = _locationPoints

    // State to track if clear operation is in progress
    private val _isClearingData = MutableStateFlow(false)
    val isClearingData: StateFlow<Boolean> = _isClearingData

    // State to track if export operation is in progress
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

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

    /**
     * Clears all location data from the database
     */
    fun clearAllLocationData() {
        viewModelScope.launch {
            try {
                _isClearingData.value = true
                val deletedCount = locationPointDao.clearAll()
                Timber.d("Cleared $deletedCount location points from database")
                // Reload empty location points list
                loadLocationPoints()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing location data")
            } finally {
                _isClearingData.value = false
            }
        }
    }

    /**
     * Exports all location data to a JSON file
     * @param context Context needed for file operations
     * @param uri Uri where the JSON file should be saved
     * @return true if export was successful, false otherwise
     */
    fun exportLocationDataToJson(context: Context, uri: Uri): Boolean {
        viewModelScope.launch {
            try {
                _isExporting.value = true

                // Get ALL location points, not just for the current vehicle
                val locations = locationPointDao.getAllLocationPoints().first()

                if (locations.isEmpty()) {
                    Timber.w("No location data to export")
                    _isExporting.value = false
                    return@launch
                }

                // Convert to JSON
                val jsonArray = JSONArray()

                // Group locations by vehicle for better organization
                val vehicleIds = locations.mapNotNull { it.vehicleLocalId }.distinct()
                Timber.d("Found ${locations.size} total locations across ${vehicleIds.size} vehicles")

                for (location in locations) {
                    val jsonObject = JSONObject().apply {
                        put("uuid", location.uuid)
                        put("localId", location.localId)
                        put("vehicleLocalId", location.vehicleLocalId)
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("altitude", location.altitude ?: JSONObject.NULL)
                        put("speed", location.speed ?: JSONObject.NULL)
                        put("accuracy", location.accuracy ?: JSONObject.NULL)
                        put("timestamp", location.timestamp)
                        put("isSynced", location.isSynced)
                        // Add a readable date for human readability
                        put("dateTime", formatTimestamp(location.timestamp))
                    }
                    jsonArray.put(jsonObject)
                }

                // Create the final JSON object with metadata
                val exportJson = JSONObject().apply {
                    put("exportDate", System.currentTimeMillis())
                    put("exportDateFormatted", formatTimestamp(System.currentTimeMillis()))
                    put("totalVehicles", vehicleIds.size)
                    put("vehicleIds", JSONArray(vehicleIds))
                    put("totalLocations", locations.size)
                    put("locations", jsonArray)
                }

                // Write to file
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(exportJson.toString(2)) // Pretty print with 2-space indentation
                        }
                    }
                }

                Timber.d("Successfully exported ${locations.size} location points to JSON")
            } catch (e: Exception) {
                Timber.e(e, "Error exporting location data to JSON")
                _isExporting.value = false
                return@launch
            }

            _isExporting.value = false
        }

        return true
    }

    // Function to format location point data for display (if needed)
    fun formatCoordinate(value: Double): String {
        return String.format("%.6f", value)
    }

    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }
} 