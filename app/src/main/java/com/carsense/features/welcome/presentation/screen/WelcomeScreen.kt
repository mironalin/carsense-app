package com.carsense.features.welcome.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carsense.features.welcome.presentation.components.ConnectionDetailsCard
import com.carsense.features.welcome.presentation.components.DisconnectFooter
import com.carsense.features.vehicles.presentation.components.SelectedVehicleCard
import com.carsense.features.welcome.presentation.components.WelcomeTopBar
import com.carsense.features.welcome.presentation.components.WelcomeBackgroundVisuals
import com.carsense.features.welcome.presentation.components.WelcomeCenterIllustration
import com.carsense.features.vehicles.presentation.viewmodel.VehicleSelectionEvent
import com.carsense.features.vehicles.presentation.viewmodel.VehicleSelectionViewModel
import com.carsense.features.welcome.presentation.viewmodel.WelcomeEvent
import com.carsense.features.welcome.presentation.viewmodel.WelcomeViewModel
import com.carsense.ui.theme.CarSenseTheme
import com.carsense.core.theme.ThemeMode
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.LogIn
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit = {},
    onDashboardClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onDarkModeToggle: () -> Unit,
    onLoginClick: () -> Unit,
    onViewAllVehicles: () -> Unit,
    isLoggedIn: Boolean = false,
    userName: String? = null,
    isConnected: Boolean = false,
    deviceName: String? = null,
    viewModel: WelcomeViewModel = hiltViewModel(),
    vehicleSelectionViewModel: VehicleSelectionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val vehicleState by vehicleSelectionViewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isLogoutLoading by remember { mutableStateOf(false) }
    var isDetailsExpanded by remember { mutableStateOf(true) }
    val scrollState = rememberScrollState()

    // Only refresh vehicles when logged in, and do it once per screen display
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            Timber.d("WelcomeScreen: Refreshing vehicles from backend")
            vehicleSelectionViewModel.onEvent(VehicleSelectionEvent.RefreshVehicles)
        }
    }

    // Clean up when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            // Any cleanup if needed
        }
    }

    BackHandler(enabled = true) {}

    Scaffold(
        snackbarHost = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .align(Alignment.BottomCenter),
                    snackbar = { snackbarData ->
                        androidx.compose.material3.Snackbar(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            shape = RoundedCornerShape(12.dp), // Match the card's corner shape
                        ) {
                            androidx.compose.material3.Text(
                                text = snackbarData.visuals.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                )
            }
        },
        topBar = {
            WelcomeTopBar(
                isLoggedIn = isLoggedIn,
                userName = userName,
                isConnected = isConnected,
                deviceName = deviceName,
                isLogoutLoading = isLogoutLoading,
                onLoginLogoutClick = onLoginClick,
                onSettingsClick = onSettingsClick,
                showSnackbar = { message ->
                    snackbarHostState.showSnackbar(message)
                })
        }, bottomBar = {
            // Only show disconnect button in the footer when connected
            if (isConnected && isLoggedIn) {
                DisconnectFooter(
                    deviceName = deviceName, onDisconnectClick = onDisconnectClick
                )
            }
        }) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Background visuals - always show for consistent visual experience
                // This should be rendered first (behind everything)
                WelcomeBackgroundVisuals(
                    modifier = Modifier.fillMaxSize()
                )
                
                // Content layer - this should be on top of background
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .verticalScroll(scrollState)
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Logo at the top -- disable for now
                    // WelcomeLogo(modifier = Modifier.fillMaxWidth())

                    // 2. Selected Vehicle Card if logged in
                    if (isLoggedIn) {
                        if (vehicleState.selectedVehicle != null) {
                            SelectedVehicleCard(
                                vehicle = vehicleState.selectedVehicle, onViewAllVehicles = {
                                    if (isConnected) {
                                        // Show snackbar instead of navigating if connected to Bluetooth
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Disconnect from the OBD2 adapter before changing vehicles")
                                        }
                                    } else {
                                        onViewAllVehicles()
                                    }
                                }, modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // If no vehicle selected, show a button to select one
                            OutlinedButton(
                                onClick = onViewAllVehicles,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp),
                            ) {
                                Icon(
                                    imageVector = Lucide.Car,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Select a vehicle",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    // 3. When not connected, show Connect button
                    if (!isConnected && isLoggedIn) {
                        Button(
                            onClick = {
                                if (!vehicleState.isVehicleSelected) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Please select a vehicle before connecting")
                                    }
                                } else {
                                    viewModel.onEvent(WelcomeEvent.Connect)
                                    onConnectClick()
                                }
                            },
                            enabled = vehicleState.isVehicleSelected,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 2.dp, pressedElevation = 4.dp
                            )
                        ) {
                            Text(
                                text = "Connect to CarSense",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // 4. When connected and logged in, show details card and dashboard button
                    if (isConnected && isLoggedIn) {
                        // Connection details card
                        ConnectionDetailsCard(
                            connectionTimeSeconds = state.connectionTimeSeconds,
                            adapterProtocol = state.adapterProtocol,
                            adapterFirmware = state.adapterFirmware,
                            onRefresh = { viewModel.onEvent(WelcomeEvent.RefreshAdapterDetails) })

                        // Go to Dashboard button
                        OutlinedButton(
                            onClick = { onDashboardClick() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                        ) {
                            Icon(
                                imageVector = Lucide.LayoutDashboard,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Go to Dashboard",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    // 5. Not logged in message with enhanced visuals
                    if (!isLoggedIn) {
                        // Add space at the top to center the content better
                        Spacer(modifier = Modifier.weight(0.5f))
                        
                        // Central illustration for engaging welcome experience
                        WelcomeCenterIllustration(
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        
                        // Enhanced button with visual candy
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Glowing background effect
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            )
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            )
                            
                            Button(
                                onClick = onLoginClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 6.dp,
                                    pressedElevation = 12.dp
                                )
                            ) {
                                Icon(
                                    imageVector = Lucide.LogIn,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Get Started",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        
                        // Add space at the bottom to balance the layout
                        Spacer(modifier = Modifier.weight(0.5f))
                    } else if (!isConnected && !vehicleState.isVehicleSelected) {
                        Text(
                            text = "Vehicle selection required to connect",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Welcome Screen Light - Logged Out")
@Composable
fun WelcomeScreenPreviewLightLoggedOut() {
    CarSenseTheme(themeMode = ThemeMode.LIGHT) {
        WelcomeScreen(
            onConnectClick = {},
            onSettingsClick = {},
            onDarkModeToggle = {},
            onLoginClick = {},
            onViewAllVehicles = {},
            isLoggedIn = false,
            userName = null,
            isConnected = false
        )
    }
}

@Preview(showBackground = true, name = "Welcome Screen Dark - Logged Out")
@Composable
fun WelcomeScreenPreviewDarkLoggedOut() {
    CarSenseTheme(themeMode = ThemeMode.DARK) {
        WelcomeScreen(
            onConnectClick = {},
            onSettingsClick = {},
            onDarkModeToggle = {},
            onLoginClick = {},
            onViewAllVehicles = {},
            isLoggedIn = false,
            userName = null,
            isConnected = false
        )
    }
}

@Preview(showBackground = true, name = "Welcome Screen Light - Logged In")
@Composable
fun WelcomeScreenPreviewLightLoggedIn() {
    CarSenseTheme(themeMode = ThemeMode.LIGHT) {
        WelcomeScreen(
            onConnectClick = {},
            onSettingsClick = {},
            onDarkModeToggle = {},
            onLoginClick = {},
            onViewAllVehicles = {},
            isLoggedIn = true,
            userName = "Alice",
            isConnected = false
        )
    }
}

@Preview(showBackground = true, name = "Welcome Screen Dark - Logged In, Connected")
@Composable
fun WelcomeScreenPreviewDarkLoggedInConnected() {
    CarSenseTheme(themeMode = ThemeMode.DARK) {
        WelcomeScreen(
            onConnectClick = {},
            onSettingsClick = {},
            onDarkModeToggle = {},
            onLoginClick = {},
            onViewAllVehicles = {},
            isLoggedIn = true,
            userName = "Bob",
            isConnected = true,
            deviceName = "ELM327"
        )
    }
}
