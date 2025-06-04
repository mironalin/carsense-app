package com.carsense.features.sensors.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.presentation.components.SensorView
import com.carsense.features.sensors.presentation.viewmodel.SensorViewModel
import com.carsense.features.sensors.presentation.viewmodel.SnapshotUploadStatus
import com.carsense.features.welcome.presentation.components.BackButton
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Thermometer
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.Upload
import com.composables.icons.lucide.Wind

/** Data class for sensor display configuration */
private data class SensorConfig(
    val id: String, val title: String, val icon: ImageVector, val reading: SensorReading?
)

/** Screen that displays sensor readings in card format */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsScreen(viewModel: SensorViewModel = hiltViewModel(), onBackPressed: () -> Unit) {
    val state by viewModel.state.collectAsState()

    // Pre-configure all sensors to prevent recreating this list on each recomposition
    val sensorConfigs by remember(state) {
        derivedStateOf {
            listOf(
                SensorConfig("rpm", "Engine RPM", Lucide.Gauge, state.rpmReading),
                SensorConfig("speed", "Vehicle Speed", Lucide.Gauge, state.speedReading),
                SensorConfig(
                    "coolant",
                    "Coolant Temperature",
                    Lucide.Thermometer,
                    state.coolantTempReading
                ),
                SensorConfig(
                    "intake",
                    "Intake Air Temperature",
                    Lucide.Wind,
                    state.intakeAirTempReading
                ),
                SensorConfig(
                    "throttle",
                    "Throttle Position",
                    Lucide.Gauge,
                    state.throttlePositionReading
                ),
                SensorConfig("fuel", "Fuel Level", Lucide.Droplet, state.fuelLevelReading),
                SensorConfig("load", "Engine Load", Lucide.Activity, state.engineLoadReading),
                SensorConfig(
                    "manifold",
                    "Intake Manifold Pressure",
                    Lucide.Gauge,
                    state.intakeManifoldPressureReading
                ),
                SensorConfig(
                    "timing",
                    "Ignition Timing Advance",
                    Lucide.Timer,
                    state.timingAdvanceReading
                ),
                SensorConfig("maf", "Mass Air Flow Rate", Lucide.Wind, state.massAirFlowReading)
            )
        }
    }

    // Stop monitoring when screen is disposed
    DisposableEffect(key1 = viewModel) {
        onDispose {
            // Stop monitoring when leaving the screen
            viewModel.stopMonitoring()
        }
    }

    // Stop monitoring when the app goes to background and restart when it comes back to
    // foreground
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Stop monitoring when app goes to background
                    viewModel.stopMonitoring()
                }

                Lifecycle.Event.ON_RESUME -> {
                    // Only restart if it was previously monitoring
                    if (state.isMonitoring) {
                        viewModel.startMonitoring()
                    }
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Status message and speed testing UI
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Sensor Readings",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    BackButton(onClick = {
                        // Stop monitoring before navigating back
                        viewModel.stopMonitoring()
                        onBackPressed()
                    })
                },
                actions = {
                    // Add the snapshot status badge 
                    SnapshotStatusBadge(
                        readingsCount = state.snapshotReadingsCount,
                        uploadStatus = state.snapshotUploadStatus,
                        isCollecting = state.snapshotCollectionInProgress
                    )

                    // Toggle monitoring button
                    IconButton(
                        onClick = {
                            if (state.isMonitoring) {
                                viewModel.stopMonitoring()
                            } else {
                                viewModel.startMonitoring()
                            }
                        }) {
                        Icon(
                            imageVector = if (state.isMonitoring) Lucide.Pause
                            else Lucide.Play,
                            contentDescription = if (state.isMonitoring) "Stop Monitoring"
                            else "Start Monitoring"
                        )
                    }

                    // Refresh button
                    IconButton(
                        onClick = {
                            viewModel.stopMonitoring()
                            viewModel.startMonitoring()
                        }) {
                        Icon(
                            imageVector = Lucide.RefreshCw, contentDescription = "Refresh Sensors"
                        )
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading && state.rpmReading == null) {
                // Show loading indicator when initially loading
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Show sensor readings using optimized LazyColumn
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Use the optimized SensorView for each sensor to prevent excessive recompositions
                    items(sensorConfigs, key = { it.id }) { config ->
                        SensorView(
                            title = config.title, icon = config.icon, sensorReading = config.reading
                        )
                    }

                    // Add snapshot status info
                    item(key = "snapshot_status") {
                        SnapshotStatusInfo(
                            readingsCount = state.snapshotReadingsCount,
                            uploadStatus = state.snapshotUploadStatus,
                            uploadError = state.snapshotUploadError,
                            isCollecting = state.snapshotCollectionInProgress,
                            lastUploadTimestamp = state.snapshotLastUploadTimestamp,
                            lastUploadReadingsCount = state.snapshotLastUploadedReadingsCount
                        )
                    }

                    // Status message with simplified text (no refresh rate info)
                    item(key = "status") {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (state.isMonitoring) "Monitoring active"
                                else "Monitoring paused",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.isMonitoring) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.6f
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Error message if any
                    state.error?.let { error ->
                        item(key = "error") {
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            )
                        }
                    }

                    // Placeholder for more sensor cards
                    item(key = "placeholder") { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun SnapshotStatusBadge(
    readingsCount: Int,
    uploadStatus: SnapshotUploadStatus,
    isCollecting: Boolean
) {
    BadgedBox(
        badge = {
            if (readingsCount > 0) {
                Badge(
                    containerColor = getStatusColor(uploadStatus, isCollecting)
                ) {
                    Text(text = readingsCount.toString())
                }
            }
        }
    ) {
        Icon(
            imageVector = Lucide.Upload,
            contentDescription = "Snapshot status",
            tint = getStatusColor(uploadStatus, isCollecting),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun SnapshotStatusInfo(
    readingsCount: Int,
    uploadStatus: SnapshotUploadStatus,
    uploadError: String?,
    isCollecting: Boolean,
    lastUploadTimestamp: Long,
    lastUploadReadingsCount: Int
) {
    val statusColor = getStatusColor(uploadStatus, isCollecting)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Lucide.Upload,
                contentDescription = "Snapshot status",
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = when {
                    isCollecting -> "Collecting snapshot: $readingsCount readings"
                    uploadStatus == SnapshotUploadStatus.SUCCESS ->
                        "Last snapshot: $lastUploadReadingsCount readings uploaded"

                    uploadStatus == SnapshotUploadStatus.ERROR -> "Snapshot upload failed"
                    else -> "No snapshot data"
                },
                color = statusColor,
                fontSize = 14.sp
            )
        }

        // Show error message if there was an error
        if (uploadStatus == SnapshotUploadStatus.ERROR && !uploadError.isNullOrEmpty()) {
            Text(
                text = uploadError,
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun getStatusColor(status: SnapshotUploadStatus, isCollecting: Boolean): Color {
    return when {
        isCollecting -> Color(0xFF2196F3) // Blue for collecting
        status == SnapshotUploadStatus.SUCCESS -> Color(0xFF4CAF50) // Green for success
        status == SnapshotUploadStatus.ERROR -> Color(0xFFF44336) // Red for error
        else -> Color.Gray
    }
}
