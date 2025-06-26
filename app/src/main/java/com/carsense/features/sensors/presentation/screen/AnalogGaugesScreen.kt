package com.carsense.features.sensors.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.carsense.features.sensors.data.SensorPreferencesManager
import com.carsense.features.sensors.domain.model.SensorReading
import com.carsense.features.sensors.presentation.screen.components.SensorSelectionDialog
import com.carsense.features.sensors.presentation.screen.components.SensorSlotCard
import com.carsense.features.sensors.presentation.screen.util.SensorConfigFactory
import com.carsense.features.sensors.presentation.screen.util.SensorPidMapping
import com.carsense.features.sensors.presentation.screen.util.SensorValueHistory
import com.carsense.features.sensors.presentation.viewmodel.SensorViewModel
import com.carsense.features.welcome.presentation.components.BackButton
import com.composables.icons.lucide.Grid3x3
import com.composables.icons.lucide.List
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RefreshCw
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SensorPreferencesEntryPoint {
    fun sensorPreferencesManager(): SensorPreferencesManager
}

/**
 * Screen that displays selected sensors as analog gauges
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalogGaugesScreen(
    viewModel: SensorViewModel = hiltViewModel(),
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val preferencesManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SensorPreferencesEntryPoint::class.java
        ).sensorPreferencesManager()
    }

    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    // UI State
    var isGridLayout by remember { mutableStateOf(false) } // false = list, true = grid
    var showSensorDialog by remember { mutableStateOf(false) }
    var selectedSlotIndex by remember { mutableStateOf(-1) } // Which slot is being filled

    // Sensor slot management (max 6 sensors)
    var sensorSlots by remember {
        mutableStateOf(
            listOf<String?>(
                null,
                null,
                null,
                null,
                null,
                null
            )
        )
    }

    // Load saved sensor slots from preferences
    LaunchedEffect(Unit) {
        val savedSlots = preferencesManager.getSensorSlots()
        sensorSlots = savedSlots.take(6).let { slots ->
            // Pad with nulls if less than 6 slots
            slots + List(6 - slots.size) { null }
        }
    }

    // Save sensor slots when they change
    LaunchedEffect(sensorSlots) {
        preferencesManager.saveSensorSlots(sensorSlots.filterNotNull())
    }

    // Convert slots to selected sensor IDs for compatibility with existing logic
    val selectedSensorIds = sensorSlots.filterNotNull().toSet()

    // Sensor history for dynamic range calculation
    var sensorHistory by remember { mutableStateOf<Map<String, SensorValueHistory>>(emptyMap()) }
    var lastReadings by remember { mutableStateOf<Map<String, SensorReading>>(emptyMap()) }

    // Generate all sensor configurations using the factory
    val allSensorConfigs by remember(state, lastReadings, selectedSensorIds, sensorHistory) {
        derivedStateOf {
            SensorConfigFactory.createAllSensorConfigs(
                state = state,
                lastReadings = lastReadings,
                selectedSensorIds = selectedSensorIds,
                sensorHistory = sensorHistory
            )
        }
    }

    // Convert selected sensor IDs to PIDs
    val selectedPids = selectedSensorIds.map { SensorPidMapping.mapSensorIdToPid(it) }.toSet()

    // Update sensor history when new readings arrive
    LaunchedEffect(
        state.rpmReading, state.speedReading, state.coolantTempReading,
        state.intakeAirTempReading, state.throttlePositionReading,
        state.fuelLevelReading, state.engineLoadReading,
        state.intakeManifoldPressureReading, state.timingAdvanceReading,
        state.massAirFlowReading
    ) {
        val newReadings = mutableMapOf<String, SensorReading>()
        val newHistory = sensorHistory.toMutableMap()

        // Map sensor state properties to sensor IDs and update readings
        val sensorMappings = mapOf(
            "rpm" to state.rpmReading,
            "speed" to state.speedReading,
            "coolant" to state.coolantTempReading,
            "intake" to state.intakeAirTempReading,
            "throttle" to state.throttlePositionReading,
            "fuel" to state.fuelLevelReading,
            "load" to state.engineLoadReading,
            "manifold" to state.intakeManifoldPressureReading,
            "timing" to state.timingAdvanceReading,
            "maf" to state.massAirFlowReading
        )

        // Process readings and update history for selected sensors
        sensorMappings.forEach { (sensorId, reading) ->
            if (selectedSensorIds.contains(sensorId) && reading != null && !reading.isError) {
                newReadings[sensorId] = reading

                // Update sensor history for dynamic range calculation
                reading.value.toFloatOrNull()?.let { floatValue ->
                    val currentHistory = newHistory[sensorId] ?: SensorValueHistory()
                    newHistory[sensorId] = currentHistory.addValue(floatValue)
                }
            }
        }

        lastReadings = newReadings
        sensorHistory = newHistory
    }

    // Lifecycle management for monitoring
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    if (selectedSensorIds.isNotEmpty()) {
                        viewModel.startSelectiveMonitoring(selectedPids)
                    } else {
                        viewModel.startMonitoring()
                    }
                }

                Lifecycle.Event.ON_STOP -> {
                    viewModel.stopMonitoring()
                }

                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analog Gauges",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    BackButton(onClick = {
                        viewModel.stopMonitoring()
                        onBackPressed()
                    })
                },
                actions = {
                    // Layout toggle button
                    IconButton(
                        onClick = { isGridLayout = !isGridLayout }
                    ) {
                        Icon(
                            imageVector = if (isGridLayout) Lucide.List else Lucide.Grid3x3,
                            contentDescription = if (isGridLayout) "Switch to List" else "Switch to Grid"
                        )
                    }

                    // Toggle monitoring button
                    IconButton(
                        onClick = {
                            if (state.isMonitoring) {
                                viewModel.stopMonitoring()
                            } else {
                                // Use selective monitoring if sensors are selected
                                if (selectedSensorIds.isNotEmpty()) {
                                    viewModel.startSelectiveMonitoring(selectedPids)
                                } else {
                                    viewModel.startMonitoring()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (state.isMonitoring) Lucide.Pause else Lucide.Play,
                            contentDescription = if (state.isMonitoring) "Stop Monitoring" else "Start Monitoring"
                        )
                    }

                    // Refresh button
                    IconButton(
                        onClick = {
                            viewModel.stopMonitoring()
                            // Use selective monitoring if sensors are selected
                            if (selectedSensorIds.isNotEmpty()) {
                                viewModel.startSelectiveMonitoring(selectedPids)
                            } else {
                                viewModel.startMonitoring()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Lucide.RefreshCw,
                            contentDescription = "Refresh"
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
            // Main content based on layout mode
            if (isGridLayout) {
                // Grid Layout: 2x3 grid with minimal padding and spacing
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(6) { index ->
                        SensorSlotCard(
                            slotIndex = index,
                            sensorId = sensorSlots[index],
                            sensorConfig = sensorSlots[index]?.let { id ->
                                allSensorConfigs.find { it.id == id }
                            },
                            state = state,
                            isGridLayout = true,
                            onAddSensor = {
                                selectedSlotIndex = index
                                showSensorDialog = true
                            },
                            onRemoveSensor = {
                                sensorSlots = sensorSlots.toMutableList().apply {
                                    set(index, null)
                                }
                            }
                        )
                    }
                }
            } else {
                // List Layout: Single column with minimal padding
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(6) { index ->
                        SensorSlotCard(
                            slotIndex = index,
                            sensorId = sensorSlots[index],
                            sensorConfig = sensorSlots[index]?.let { id ->
                                allSensorConfigs.find { it.id == id }
                            },
                            state = state,
                            isGridLayout = false,
                            onAddSensor = {
                                selectedSlotIndex = index
                                showSensorDialog = true
                            },
                            onRemoveSensor = {
                                sensorSlots = sensorSlots.toMutableList().apply {
                                    set(index, null)
                                }
                            }
                        )
                    }
                }
            }

            // Sensor Selection Dialog
            if (showSensorDialog) {
                SensorSelectionDialog(
                    availableSensors = allSensorConfigs.filter { config ->
                        !sensorSlots.contains(config.id) // Only show sensors not already added
                    },
                    onSensorSelected = { sensorId ->
                        sensorSlots = sensorSlots.toMutableList().apply {
                            set(selectedSlotIndex, sensorId)
                        }
                        showSensorDialog = false
                        selectedSlotIndex = -1
                    },
                    onDismiss = {
                        showSensorDialog = false
                        selectedSlotIndex = -1
                    }
                )
            }
        }
    }
} 