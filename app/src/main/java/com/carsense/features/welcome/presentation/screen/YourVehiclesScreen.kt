package com.carsense.features.welcome.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch
import timber.log.Timber
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.slideOutHorizontally

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
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
    val coroutineScope = rememberCoroutineScope()

    // Create a SnackbarHostState
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect vehicle state
    val state by viewModel.state.collectAsState()

    // Show error messages in snackbar
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(message = it)
        }
    }

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

    // Pull to refresh state
    val refreshing = state.isLoading
    val pullRefreshState = rememberPullRefreshState(
        refreshing = refreshing, onRefresh = {
            coroutineScope.launch {
                viewModel.onEvent(VehicleSelectionEvent.RefreshVehicles)
            }
        })

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        topBar = {
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
        }
    ) { paddingValues ->
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Only apply pullRefresh when there are vehicles
                        .then(
                            if (state.vehicles.isNotEmpty()) {
                                Modifier.pullRefresh(pullRefreshState)
                            } else {
                                Modifier
                            }
                        )
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
                                // Only show the CircularProgressIndicator when loading initially (not during pull refresh)
                                if (state.isLoading && !refreshing) {
                                    CircularProgressIndicator()
                                } else {
                                    NoVehiclesCard(
                                        isRefreshing = state.isLoading, onRefresh = {
                                            coroutineScope.launch {
                                                viewModel.onEvent(VehicleSelectionEvent.RefreshVehicles)
                                            }
                                        })
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
                                    val currentVehicle by rememberUpdatedState(vehicle)
                                    // Track if this item is being deleted to handle immediate UI feedback
                                    val isItemBeingDeleted = remember { mutableStateOf(false) }
                                    val coroutineScope = rememberCoroutineScope()

                                    // Use AnimatedVisibility to animate item removal
                                    AnimatedVisibility(
                                        visible = !isItemBeingDeleted.value,
                                        exit = slideOutHorizontally(
                                            targetOffsetX = { fullWidth -> -fullWidth },
                                            animationSpec = tween(durationMillis = 300)
                                        ) + fadeOut(animationSpec = tween(durationMillis = 300))
                                    ) {
                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { dismissValue ->
                                                if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                                    // Mark as being deleted immediately for UI update
                                                    isItemBeingDeleted.value = true

                                                    // Perform actual deletion after UI update
                                                    coroutineScope.launch {
                                                        viewModel.onEvent(
                                                            VehicleSelectionEvent.DeleteVehicle(
                                                                currentVehicle.uuid
                                                            )
                                                        )
                                                    }
                                                    true // Confirm the dismiss
                                                } else {
                                                    false // Do not dismiss for other directions
                                                }
                                            },
                                            positionalThreshold = { totalDistance -> totalDistance * 0.40f } // Increased from 0.10f to 0.40f
                                        )

                                        SwipeToDismissBox(
                                            state = dismissState,
                                            enableDismissFromStartToEnd = false, // Disable swipe from left to right
                                            enableDismissFromEndToStart = true,   // Enable swipe from right to left
                                            backgroundContent = {
                                                val color = when (dismissState.dismissDirection) {
                                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                                    else -> Color.Transparent
                                                }
                                                val scale by animateFloatAsState(
                                                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) 1f else 0.75f,
                                                    label = "dismiss_icon_scale"
                                                )

                                                Box(
                                                    Modifier
                                                        .fillMaxSize()
                                                        .padding(vertical = 6.dp) // Match the card's vertical padding
                                                        .clip(RoundedCornerShape(12.dp)) // Match the card's corner shape 
                                                        .background(color)
                                                        .padding(horizontal = 20.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete Vehicle",
                                                        modifier = Modifier.scale(scale),
                                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                }
                                            }
                                        ) {
                                            VehicleSelectionCard(
                                                vehicle = currentVehicle,
                                                onSelect = { uuid ->
                                                    // Pause data loading before navigation
                                                    viewModel.pauseDataLoading()
                                                    viewModel.onEvent(
                                                        VehicleSelectionEvent.SelectVehicle(
                                                            uuid
                                                        )
                                                    )
                                                    onBackPressed()
                                                },
                                                showDetailedInfo = true
                                            )
                                        }
                                    }
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

                    // Only show pull refresh indicator when there are vehicles
                    if (state.vehicles.isNotEmpty()) {
                        PullRefreshIndicator(
                            refreshing = refreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                            backgroundColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoVehiclesCard(
    isRefreshing: Boolean = false, onRefresh: () -> Unit
) {
    // Animation for the card
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f, animationSpec = tween(500), label = "card_scale"
    )

    // Continuous pulsing animation for the icon
    val infiniteTransition = rememberInfiniteTransition(label = "infinite_pulse")
    val iconPulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f, animationSpec = infiniteRepeatable(
            animation = tween(1000), repeatMode = RepeatMode.Reverse
        ), label = "icon_pulse"
    )

    LaunchedEffect(Unit) {
        isVisible = true
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .scale(scale),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .height(280.dp), // Fixed height for consistent sizing
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center // Center content vertically
        ) {
            if (isRefreshing) {
                // Loading state
                Box(
                    modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Refreshing...",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Looking for your vehicles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Invisible spacer to maintain consistent height
                Spacer(modifier = Modifier.height(62.dp))
            } else {
                // Normal state
                Icon(
                    imageVector = Icons.Filled.DirectionsCar,
                    contentDescription = "No vehicles",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(72.dp)
                        .scale(iconPulse)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "No vehicles found",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Add a vehicle or refresh to check for updates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onRefresh
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh, contentDescription = "Refresh"
                        )
                        Text("Refresh")
                    }
                }
            }
        }
    }
} 