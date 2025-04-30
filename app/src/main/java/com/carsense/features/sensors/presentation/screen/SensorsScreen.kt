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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.presentation.components.SensorView
import com.carsense.features.sensors.presentation.viewmodel.SensorViewModel
import com.composables.icons.lucide.Activity
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Droplet
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Thermometer
import com.composables.icons.lucide.Timer
import com.composables.icons.lucide.Wind

/** Data class for sensor display configuration */
private data class SensorConfig(
        val id: String,
        val title: String,
        val icon: ImageVector,
        val reading: SensorReading?
)

/** Screen that displays sensor readings in card format */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsScreen(viewModel: SensorViewModel = hiltViewModel(), onBackPressed: () -> Unit) {
    val state by viewModel.state.collectAsState()
    var showRefreshRateMenu by remember { mutableStateOf(false) }

    // Pre-configure all sensors to prevent recreating this list on each recomposition
    val sensorConfigs by
            remember(state) {
                derivedStateOf {
                    listOf(
                            SensorConfig("rpm", "Engine RPM", Lucide.Gauge, state.rpmReading),
                            SensorConfig(
                                    "speed",
                                    "Vehicle Speed",
                                    Lucide.Gauge,
                                    state.speedReading
                            ),
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
                            SensorConfig(
                                    "fuel",
                                    "Fuel Level",
                                    Lucide.Droplet,
                                    state.fuelLevelReading
                            ),
                            SensorConfig(
                                    "load",
                                    "Engine Load",
                                    Lucide.Activity,
                                    state.engineLoadReading
                            ),
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
                            SensorConfig(
                                    "maf",
                                    "Mass Air Flow Rate",
                                    Lucide.Wind,
                                    state.massAirFlowReading
                            )
                    )
                }
            }

    // Stop monitoring when screen is disposed
    DisposableEffect(key1 = viewModel) {
        onDispose {
            // Stop monitoring when leaving the screen
            viewModel.stopReadings()
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
                    viewModel.stopReadings()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Only restart if it was previously monitoring
                    if (state.isMonitoring) {
                        viewModel.startReadings()
                    }
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Refresh rate options
    val refreshRateOptions =
            listOf(
                    RefreshRateOption(200, "0.2s"),
                    RefreshRateOption(500, "0.5s"),
                    RefreshRateOption(1000, "1s"),
                    RefreshRateOption(2000, "2s"),
                    RefreshRateOption(5000, "5s")
            )

    // Get the color for the refresh rate icon based on the current refresh rate
    val refreshRateIconColor =
            when (state.refreshRateMs) {
                200L -> Color(0xFFE57373) // Light Red for fastest (high battery usage)
                500L -> Color(0xFFFFB74D) // Light Orange for fast
                1000L -> MaterialTheme.colorScheme.primary // Primary color for normal
                2000L -> Color(0xFF64B5F6) // Light Blue for slow
                5000L -> Color(0xFF81C784) // Light Green for slowest (low battery usage)
                else -> MaterialTheme.colorScheme.primary
            }

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
                            IconButton(
                                    onClick = {
                                        // Stop monitoring before navigating back
                                        viewModel.stopReadings()
                                        onBackPressed()
                                    }
                            ) { Icon(imageVector = Lucide.ArrowLeft, contentDescription = "Back") }
                        },
                        actions = {
                            // Refresh rate selector button and dropdown
                            Box {
                                IconButton(onClick = { showRefreshRateMenu = true }) {
                                    Icon(
                                            imageVector = Lucide.Clock,
                                            contentDescription = "Refresh Rate",
                                            tint = refreshRateIconColor
                                    )
                                }

                                DropdownMenu(
                                        expanded = showRefreshRateMenu,
                                        onDismissRequest = { showRefreshRateMenu = false }
                                ) {
                                    Text(
                                            text = "Refresh Rate",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier =
                                                    Modifier.padding(
                                                            horizontal = 16.dp,
                                                            vertical = 4.dp
                                                    )
                                    )

                                    refreshRateOptions.forEach { option ->
                                        val itemColor =
                                                when (option.rateMs) {
                                                    200L -> Color(0xFFE57373) // Light
                                                    // Red
                                                    500L -> Color(0xFFFFB74D) // Light
                                                    // Orange
                                                    1000L ->
                                                            MaterialTheme.colorScheme
                                                                    .primary // Primary
                                                    2000L -> Color(0xFF64B5F6) // Light
                                                    // Blue
                                                    5000L -> Color(0xFF81C784) // Light
                                                    // Green
                                                    else -> MaterialTheme.colorScheme.primary
                                                }

                                        DropdownMenuItem(
                                                text = {
                                                    Row(
                                                            verticalAlignment =
                                                                    Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                                imageVector = Lucide.Clock,
                                                                contentDescription = null,
                                                                tint = itemColor,
                                                                modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                                text = option.label,
                                                                fontWeight =
                                                                        if (state.refreshRateMs ==
                                                                                        option.rateMs
                                                                        )
                                                                                FontWeight.Bold
                                                                        else FontWeight.Normal,
                                                                color =
                                                                        if (state.refreshRateMs ==
                                                                                        option.rateMs
                                                                        )
                                                                                itemColor
                                                                        else
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                                text =
                                                                        when (option.rateMs) {
                                                                            200L ->
                                                                                    "(experimental, may overwhelm OBD)"
                                                                            500L ->
                                                                                    "(high battery usage)"
                                                                            5000L ->
                                                                                    "(lower battery usage)"
                                                                            else -> ""
                                                                        },
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color =
                                                                        if (option.rateMs == 200L)
                                                                                Color(0xFFE57373)
                                                                        else
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurface
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.6f
                                                                                        )
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.setRefreshRate(option.rateMs)
                                                    showRefreshRateMenu = false
                                                }
                                        )
                                    }
                                }
                            }

                            // Toggle monitoring button
                            IconButton(
                                    onClick = {
                                        if (state.isMonitoring) {
                                            viewModel.stopReadings()
                                        } else {
                                            viewModel.startReadings()
                                        }
                                    }
                            ) {
                                Icon(
                                        imageVector =
                                                if (state.isMonitoring) Lucide.Pause
                                                else Lucide.Play,
                                        contentDescription =
                                                if (state.isMonitoring) "Stop" else "Start"
                                )
                            }

                            // Refresh button
                            IconButton(onClick = { viewModel.refreshSensors() }) {
                                Icon(imageVector = Lucide.RefreshCw, contentDescription = "Refresh")
                            }
                        },
                        modifier = Modifier.statusBarsPadding()
                )
            }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
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
                    // Use the optimized SensorView for each sensor to prevent excessive
                    // recompositions
                    items(sensorConfigs, key = { it.id }) { config ->
                        SensorView(
                                title = config.title,
                                icon = config.icon,
                                sensorReading = config.reading
                        )
                    }

                    // Status message with refresh rate info
                    item(key = "status") {
                        Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                        ) {
                            Text(
                                    text =
                                            if (state.isMonitoring) "Monitoring active"
                                            else "Monitoring paused",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color =
                                            if (state.isMonitoring)
                                                    MaterialTheme.colorScheme.primary
                                            else
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                            alpha = 0.6f
                                                    ),
                                    textAlign = TextAlign.Center
                            )

                            if (state.isMonitoring) {
                                Text(
                                        text =
                                                "Refresh rate: ${refreshRateOptions.find { it.rateMs == state.refreshRateMs }?.label ?: "0.6s"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = refreshRateIconColor,
                                        textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Error message if any
                    state.error?.let { error ->
                        item(key = "error") {
                            Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
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

/** Data class for refresh rate options */
private data class RefreshRateOption(val rateMs: Long, val label: String)
