package com.carsense.features.sensors.data.service

import android.util.Log
import com.carsense.features.diagnostics.data.DiagnosticSessionManager
import com.carsense.features.sensors.data.api.CreateSensorSnapshotRequest
import com.carsense.features.sensors.data.api.SensorApiService
import com.carsense.features.sensors.data.api.SensorReadingRequest
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.presentation.viewmodel.SensorViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Collects sensor readings for creating snapshots that represent full priority cycles.
 * This service tracks sensor readings as they come in and uploads them to the backend
 * once a complete set (exactly 19 readings - matching the expected count) has been collected.
 */
@Singleton
class SensorSnapshotCollector @Inject constructor(
    private val sensorApiService: SensorApiService,
    private val diagnosticSessionManager: DiagnosticSessionManager
) {
    private val TAG = "SensorSnapshotCollector"

    // Required sensor PIDs - we need at least one reading from each of these
    private val requiredSensorPids = setOf(
        SensorViewModel.PID_RPM,                   // High priority
        SensorViewModel.PID_SPEED,                 // High priority  
        SensorViewModel.PID_THROTTLE_POSITION,     // High priority
        SensorViewModel.PID_COOLANT_TEMP,          // Medium priority
        SensorViewModel.PID_ENGINE_LOAD,           // Medium priority
        SensorViewModel.PID_INTAKE_MANIFOLD_PRESSURE, // Medium priority
        SensorViewModel.PID_INTAKE_AIR_TEMP,       // Low priority
        SensorViewModel.PID_FUEL_LEVEL,            // Low priority
        SensorViewModel.PID_TIMING_ADVANCE,        // Low priority
        SensorViewModel.PID_MAF_RATE               // Low priority
    )

    // Store all readings without limiting per sensor
    // This ensures we capture all data points from the round-robin polling

    // Date formatter for ISO timestamps
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // No fixed expected reading count - collect all readings
    // This allows the round-robin approach to include all collected sensor readings

    // Current cycle ID to track readings from the same cycle
    private var currentCycleId = UUID.randomUUID().toString()

    // Time when the cycle started - used for better grouping
    private var cycleStartTime = System.currentTimeMillis()

    // Time window for considering readings part of the same cycle (5 seconds)
    private val CYCLE_WINDOW_MS = 5000L

    // Coroutine scope for background operations
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // State management
    private val _state = MutableStateFlow(SnapshotCollectorState())
    val state: StateFlow<SnapshotCollectorState> = _state.asStateFlow()

    /**
     * Add a sensor reading to the current collection.
     *
     * @param reading The sensor reading to add
     * @return true if this reading completed a full circle snapshot
     */
    fun addReading(reading: SensorReading): Boolean {
        if (reading.isError) {
            return false
        }

        val pid = reading.pid
        val value = reading.value.toDoubleOrNull() ?: return false

        // Check if this is a sensor we care about
        if (!requiredSensorPids.contains(pid)) {
            return false
        }

        // Add reading to appropriate collection for this sensor
        val readingData = SensorReadingData(
            value = value,
            unit = reading.unit,
            timestamp = reading.timestamp,
            cycleId = currentCycleId
        )

        // Debug log readings as they come in
        Log.d(
            TAG,
            "Adding reading: PID=$pid value=$value unit=${reading.unit} time=${reading.timestamp}"
        )

        // Update state with new reading
        _state.update { currentState ->
            // Get current readings for this sensor
            val currentReadings = currentState.readings[pid] ?: emptyList()

            // Add new reading without limiting - store all readings
            val newReadings = currentReadings + readingData

            // Update the list of collected sensors
            val collectedSensors = if (currentReadings.isEmpty()) {
                // Log when we add a new sensor type
                Log.d(
                    TAG,
                    "Added new sensor type: $pid (${requiredSensorPids.size - currentState.collectedSensors.size - 1} remaining)"
                )
                currentState.collectedSensors + pid
            } else {
                currentState.collectedSensors
            }

            // Update the collection
            val updatedReadings = currentState.readings.toMutableMap().apply {
                put(pid, newReadings)
            }

            // Count total readings for current cycle
            val currentCycleReadings = updatedReadings.values
                .flatten()
                .count { it.cycleId == currentCycleId }

            // Check if we've collected all required sensor types
            val hasAllRequiredSensors = collectedSensors.containsAll(requiredSensorPids)

            // A full cycle is complete when all required sensors are present - no minimum count
            val hasFullCircle = hasAllRequiredSensors

            // Check if we should upload
            val isReadyToUpload = hasFullCircle

            // Reset cycle if it's been too long
            val currentTime = System.currentTimeMillis()
            val cycleAge = currentTime - cycleStartTime
            val cycleExpired = cycleAge > CYCLE_WINDOW_MS

            // If the cycle has expired, start a new one (unless we just completed it)
            if (cycleExpired && !isReadyToUpload) {
                Log.d(TAG, "Cycle $currentCycleId expired after ${cycleAge}ms, starting new cycle")
                startNewCycle()
                return@update currentState.copy(
                    readings = updatedReadings,
                    collectedSensors = collectedSensors,
                    currentCycleReadingsCount = currentCycleReadings
                )
            }

            // Update state
            currentState.copy(
                readings = updatedReadings,
                collectedSensors = collectedSensors,
                isReadyToUpload = isReadyToUpload,
                currentCycleReadingsCount = currentCycleReadings
            )
        }

        // Check if we've collected a full circle and are ready to upload
        val currentState = state.value
        if (currentState.isReadyToUpload) {
            // Double-check that we have ALL required sensors before proceeding
            val missingRequiredSensors = requiredSensorPids - currentState.collectedSensors

            if (missingRequiredSensors.isNotEmpty()) {
                Log.e(TAG, "BLOCKING SNAPSHOT: Missing required sensors: $missingRequiredSensors")
                return false
            }

            // Extract readings for the current cycle before starting the upload
            val readingsForUpload = extractReadingsForCycle(currentCycleId)

            // Log which sensors were collected
            val collectedPids = currentState.collectedSensors
            val missingPids = requiredSensorPids - collectedPids

            if (missingPids.isNotEmpty()) {
                Log.w(TAG, "Uploading snapshot with missing sensors: $missingPids")
            } else {
                Log.d(TAG, "Collected all required sensors for snapshot")
            }

            Log.d(
                TAG,
                "Collected full snapshot with ${readingsForUpload.size} readings. First reading: " +
                        "${readingsForUpload.firstOrNull()?.pid}, Last reading: ${readingsForUpload.lastOrNull()?.pid}"
            )

            // Start a new cycle immediately
            val oldCycleId = currentCycleId
            startNewCycle()

            // Log that we're uploading a complete snapshot
            Log.i(
                TAG,
                "Uploading complete snapshot with all ${readingsForUpload.size} readings (using round-robin collection)"
            )

            // Upload the snapshot in the background
            serviceScope.launch {
                uploadSnapshot(readingsForUpload, oldCycleId)
            }

            return true
        }

        return false
    }

    /**
     * Start a new collection cycle
     */
    private fun startNewCycle() {
        currentCycleId = UUID.randomUUID().toString()
        cycleStartTime = System.currentTimeMillis()

        // Clear all previous readings to start fresh
        _state.update { currentState ->
            SnapshotCollectorState(
                lastUploadStatus = currentState.lastUploadStatus,
                lastSnapshotTimestamp = currentState.lastSnapshotTimestamp,
                lastUploadedReadingsCount = currentState.lastUploadedReadingsCount,
                lastUploadError = currentState.lastUploadError
            )
        }
    }

    /**
     * Extract readings for a specific cycle ID.
     */
    private fun extractReadingsForCycle(cycleId: String): List<SensorReadingRequest> {
        val currentState = state.value

        // Final safety check - verify all sensors are present
        val missingExtractSensors = requiredSensorPids - currentState.collectedSensors
        if (missingExtractSensors.isNotEmpty()) {
            Log.e(TAG, "Cannot extract readings: missing required sensors: $missingExtractSensors")
            // Return empty list to prevent incomplete snapshots
            return emptyList()
        }

        val allReadings = mutableListOf<SensorReadingWithTime>()

        // Collect all readings with their timestamps for sorting
        currentState.readings.forEach { (pid, readingList) ->
            readingList
                .filter { it.cycleId == cycleId }
                .forEach { readingData ->
                    allReadings.add(
                        SensorReadingWithTime(
                            timestamp = readingData.timestamp,
                            request = SensorReadingRequest(
                                pid = pid,
                                value = readingData.value,
                                unit = readingData.unit,
                                timestamp = formatTimestamp(readingData.timestamp)
                            )
                        )
                    )
                }
        }

        // Log every reading being included in the snapshot
        allReadings.sortedBy { it.timestamp }.forEach { reading ->
            Log.d(
                TAG,
                "Snapshot reading: PID=${reading.request.pid} time=${reading.timestamp} value=${reading.request.value}"
            )
        }

        // Return readings sorted by timestamp
        return allReadings.sortedBy { it.timestamp }.map { it.request }
    }

    /**
     * Helper class to sort readings by timestamp
     */
    private data class SensorReadingWithTime(
        val timestamp: Long,
        val request: SensorReadingRequest
    )

    /**
     * Convert a timestamp in milliseconds to an ISO-8601 date string.
     */
    private fun formatTimestamp(timestamp: Long): String {
        return isoDateFormat.format(Date(timestamp))
    }

    /**
     * Upload a snapshot with the provided readings.
     */
    private suspend fun uploadSnapshot(readings: List<SensorReadingRequest>, cycleId: String) {
        if (readings.isEmpty()) {
            Log.e(TAG, "Cannot upload empty readings list")
            _state.update {
                it.copy(
                    isUploading = false,
                    lastUploadStatus = UploadStatus.ERROR,
                    lastUploadError = "No readings to upload"
                )
            }
            return
        }

        // Check if we have a valid session
        val currentSessionId = diagnosticSessionManager.getCurrentDiagnosticUUID()
        if (currentSessionId == null) {
            Log.e(TAG, "Cannot upload snapshot: No active diagnostic session")
            _state.update {
                it.copy(
                    isUploading = false,
                    lastUploadStatus = UploadStatus.ERROR,
                    lastUploadError = "No active diagnostic session"
                )
            }
            return
        }

        // Update state to indicate upload in progress
        _state.update { it.copy(isUploading = true) }

        // Log that we're starting the upload
        val readingsCount = readings.size
        Log.d(
            TAG,
            "Uploading snapshot with $readingsCount readings to diagnostic $currentSessionId"
        )

        val request = CreateSensorSnapshotRequest(
            source = "obd2",
            readings = readings
        )

        // Launch coroutine for async upload
        serviceScope.launch {
            try {
                // Submit the request
                val response = sensorApiService.createSensorSnapshot(
                    diagnosticUUID = currentSessionId,
                    request = request
                )

                // Update state with success
                _state.update { currentState ->
                    currentState.copy(
                        isUploading = false,
                        lastUploadStatus = UploadStatus.SUCCESS,
                        lastSnapshotTimestamp = System.currentTimeMillis(),
                        lastUploadedReadingsCount = readingsCount,
                        lastUploadError = null
                    )
                }

                Log.d(
                    TAG,
                    "Successfully uploaded snapshot with ${readings.size} readings: ${response.body()?.snapshot?.uuid}"
                )
            } catch (e: IOException) {
                // Handle network errors
                Log.e(TAG, "Network error uploading snapshot", e)
                _state.update { currentState ->
                    currentState.copy(
                        isUploading = false,
                        lastUploadStatus = UploadStatus.ERROR,
                        lastUploadError = "Network error: ${e.message}"
                    )
                }
            } catch (e: Exception) {
                // Handle other errors
                Log.e(TAG, "Error uploading snapshot", e)
                _state.update { currentState ->
                    currentState.copy(
                        isUploading = false,
                        lastUploadStatus = UploadStatus.ERROR,
                        lastUploadError = "Error: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Clear the current collection and start a new cycle.
     */
    fun clearCollection() {
        startNewCycle()
    }

    /**
     * Data class representing a sensor reading for snapshot collection.
     */
    data class SensorReadingData(
        val value: Double,
        val unit: String,
        val timestamp: Long,
        val cycleId: String
    )

    /**
     * Status of the last snapshot upload.
     */
    enum class UploadStatus {
        NONE,
        SUCCESS,
        ERROR
    }

    /**
     * State for the snapshot collector.
     */
    data class SnapshotCollectorState(
        val readings: Map<String, List<SensorReadingData>> = emptyMap(),
        val collectedSensors: Set<String> = emptySet(),
        val isReadyToUpload: Boolean = false,
        val isUploading: Boolean = false,
        val currentCycleReadingsCount: Int = 0,
        val lastUploadStatus: UploadStatus = UploadStatus.NONE,
        val lastUploadError: String? = null,
        val lastSnapshotTimestamp: Long = 0,
        val lastUploadedReadingsCount: Int = 0
    )
} 