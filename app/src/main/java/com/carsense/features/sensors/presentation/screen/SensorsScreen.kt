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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.presentation.viewmodel.SensorViewModel
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RefreshCw

/** Screen that displays sensor readings in card format */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsScreen(viewModel: SensorViewModel = hiltViewModel(), onBackPressed: () -> Unit) {
    val state by viewModel.state.collectAsState()

    // Stop monitoring when screen is disposed
    DisposableEffect(key1 = viewModel) {
        onDispose {
            // Stop monitoring when leaving the screen
            viewModel.stopReadings()
        }
    }

    // Stop monitoring when the app goes to background and restart when it comes back to foreground
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
                // Show sensor readings
                LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // RPM Card
                    item { RPMSensorCard(sensorReading = state.rpmReading) }

                    // Status message
                    item {
                        Text(
                                text =
                                        if (state.isMonitoring) "Monitoring active"
                                        else "Monitoring paused",
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                        if (state.isMonitoring) MaterialTheme.colorScheme.primary
                                        else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                        alpha = 0.6f
                                                ),
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                textAlign = TextAlign.Center
                        )
                    }

                    // Error message if any
                    state.error?.let { error ->
                        item {
                            Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                            )
                        }
                    }

                    // Placeholder for more sensor cards
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

/** Card that displays RPM sensor reading */
@Composable
fun RPMSensorCard(sensorReading: SensorReading?) {
    Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.Start) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        imageVector = Lucide.Gauge,
                        contentDescription = "RPM",
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.size(12.dp))

                Text(
                        text = "Engine RPM",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (sensorReading == null) {
                Text(
                        text = "No data available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else if (sensorReading.isError) {
                Text(
                        text = sensorReading.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                        text = sensorReading.value,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                )

                Text(
                        text = sensorReading.unit,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
