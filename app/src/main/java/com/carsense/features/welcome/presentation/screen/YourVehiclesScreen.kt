package com.carsense.features.welcome.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.carsense.features.welcome.presentation.components.AddVehicleButton
import com.carsense.features.welcome.presentation.components.BackButton
import com.carsense.features.welcome.presentation.components.VehicleSelectionCard
import com.carsense.features.welcome.presentation.viewmodel.VehicleSelectionEvent
import com.carsense.features.welcome.presentation.viewmodel.VehicleSelectionViewModel
import kotlinx.coroutines.delay
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YourVehiclesScreen(
    onBackPressed: () -> Unit,
    onAddVehicle: () -> Unit,
    viewModel: VehicleSelectionViewModel = hiltViewModel()
) {
    // Screen initialization state that persists across recompositions
    var isInitialized by rememberSaveable { mutableStateOf(false) }
    var showContent by rememberSaveable { mutableStateOf(false) }

    // Handle lifecycle events
    val lifecycleOwner = LocalLifecycleOwner.current

    // First-time initialization
    LaunchedEffect(Unit) {
        if (!isInitialized) {
            delay(100) // Short delay for smoother transition
            viewModel.pauseDataLoading() // Ensure clean state
            isInitialized = true
            delay(150) // Wait for transition animation
            viewModel.resumeDataLoading() // Start loading data
            showContent = true
        }
    }

    // Monitor lifecycle events to properly manage data loading
    DisposableEffect(lifecycleOwner) {
        Timber.d("YourVehiclesScreen: Setting up lifecycle observer")
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Timber.d("YourVehiclesScreen: ON_CREATE")
                }

                Lifecycle.Event.ON_START -> {
                    Timber.d("YourVehiclesScreen: ON_START")
                }

                Lifecycle.Event.ON_RESUME -> {
                    Timber.d("YourVehiclesScreen: ON_RESUME")
                    // Only resume data loading if the screen is already initialized
                    if (isInitialized) {
                        viewModel.resumeDataLoading()
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    Timber.d("YourVehiclesScreen: ON_PAUSE - Pausing data loading")
                    viewModel.pauseDataLoading()
                }

                Lifecycle.Event.ON_STOP -> {
                    Timber.d("YourVehiclesScreen: ON_STOP")
                }

                Lifecycle.Event.ON_DESTROY -> {
                    Timber.d("YourVehiclesScreen: ON_DESTROY")
                }

                else -> { /* ignore other events */
                }
            }
        }

        // Add the observer
        lifecycleOwner.lifecycle.addObserver(observer)

        // Clean up on dispose
        onDispose {
            Timber.d("YourVehiclesScreen: Disposing - Removing lifecycle observer")
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Ensure data loading is stopped
            viewModel.pauseDataLoading()
        }
    }

    // Collect the state safely
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }, topBar = {
        TopAppBar(
            title = { Text("Your Vehicles") }, navigationIcon = {
                BackButton(onClick = {
                    // Ensure data loading is paused before navigation
                    viewModel.pauseDataLoading()
                    onBackPressed()
                })
            }, colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
    }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Show loading indicator until content is ready
            if (!showContent) {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Content appears with fade-in animation when ready
            AnimatedVisibility(
                visible = showContent, enter = fadeIn(initialAlpha = 0.3f), exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    if (state.vehicles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator()
                            } else {
                                Text(
                                    text = "No vehicles found. Add a vehicle to continue.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(
                                items = state.vehicles,
                                key = { vehicle -> vehicle.uuid }) { vehicle ->
                                VehicleSelectionCard(
                                    vehicle = vehicle, onSelect = { uuid ->
                                        // Pause data loading before navigation
                                        viewModel.pauseDataLoading()
                                        viewModel.onEvent(VehicleSelectionEvent.SelectVehicle(uuid))
                                        onBackPressed()
                                    }, showDetailedInfo = true
                                )
                            }
                        }
                    }

                    // Add vehicle button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        AddVehicleButton(
                            onClick = {
                                // Pause data loading before navigating to Add Vehicle screen
                                viewModel.pauseDataLoading()
                                onAddVehicle()
                            })
                    }
                }
            }
        }
    }
} 