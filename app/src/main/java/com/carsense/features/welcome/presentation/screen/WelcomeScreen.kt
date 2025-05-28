package com.carsense.features.welcome.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carsense.features.welcome.presentation.components.ConnectionDetailsCard
import com.carsense.features.welcome.presentation.components.DisconnectFooter
import com.carsense.features.welcome.presentation.components.WelcomeLogo
import com.carsense.features.welcome.presentation.components.WelcomeTopBar
import com.carsense.features.welcome.presentation.viewmodel.WelcomeEvent
import com.carsense.features.welcome.presentation.viewmodel.WelcomeViewModel
import com.carsense.ui.theme.CarSenseTheme
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.Lucide
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit = {},
    onDashboardClick: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onDarkModeToggle: () -> Unit,
    onLoginClick: () -> Unit,
    isLoggedIn: Boolean = false,
    userName: String? = null,
    isConnected: Boolean = false,
    deviceName: String? = null,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isLogoutLoading by remember { mutableStateOf(false) }
    var isDetailsExpanded by remember { mutableStateOf(true) }

    BackHandler(enabled = true) {}

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { snackbarData ->
                    Snackbar(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        snackbarData = snackbarData
                    )
                }
            )
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
                }
            )
        },
        bottomBar = {
            // Only show disconnect button in the footer when connected
            if (isConnected && isLoggedIn) {
                DisconnectFooter(
                    deviceName = deviceName,
                    onDisconnectClick = onDisconnectClick
                )
            }
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                WelcomeLogo()

                Spacer(modifier = Modifier.height(32.dp))

                // When not connected, show Connect button
                if (!isConnected) {
                    Button(
                        onClick = {
                            if (!isLoggedIn) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Please log in to connect to your OBD2 device")
                                }
                            } else {
                                viewModel.onEvent(WelcomeEvent.Connect)
                                onConnectClick()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(min = 56.dp),
                        elevation = ButtonDefaults.buttonElevation(
                            defaultElevation = 2.dp,
                            pressedElevation = 4.dp
                        )
                    ) {
                        Text(
                            text = "Connect to CarSense",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                // When connected and logged in, show details card and dashboard button
                if (isConnected && isLoggedIn) {
                    // 1. Connection details card first
                    ConnectionDetailsCard(
                        connectionTimeSeconds = state.connectionTimeSeconds,
                        adapterProtocol = state.adapterProtocol,
                        adapterFirmware = state.adapterFirmware,
                        onRefresh = { viewModel.onEvent(WelcomeEvent.RefreshAdapterDetails) }
                    )

                    // 2. Go to Dashboard button second
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { onDashboardClick() },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
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

                if (!isLoggedIn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Login required to connect",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Welcome Screen Light - Logged Out")
@Composable
fun WelcomeScreenPreviewLightLoggedOut() {
    CarSenseTheme(darkTheme = false) {
        WelcomeScreen(
            onConnectClick = {},
            onSettingsClick = {},
            onDarkModeToggle = {},
            onLoginClick = {},
            isLoggedIn = false,
            userName = null,
            isConnected = false
        )
    }
}

@Preview(showBackground = true, name = "Welcome Screen Dark - Logged Out")
@Composable
fun WelcomeScreenPreviewDarkLoggedOut() {
    CarSenseTheme(darkTheme = true) {
        WelcomeScreen(
            onConnectClick = {},
            onSettingsClick = {},
            onDarkModeToggle = {},
            onLoginClick = {},
            isLoggedIn = false,
            userName = null,
            isConnected = false
        )
    }
}

@Preview(showBackground = true, name = "Welcome Screen Light - Logged In")
@Composable
fun WelcomeScreenPreviewLightLoggedIn() {
    CarSenseTheme(darkTheme = false) {
        WelcomeScreen(
            onConnectClick = {},
            onSettingsClick = {},
            onDarkModeToggle = {},
            onLoginClick = {},
            isLoggedIn = true,
            userName = "Alice",
            isConnected = false
        )
    }
}

@Preview(showBackground = true, name = "Welcome Screen Dark - Logged In, Connected")
@Composable
fun WelcomeScreenPreviewDarkLoggedInConnected() {
    CarSenseTheme(darkTheme = true) {
        WelcomeScreen(
            onConnectClick = {},
            onSettingsClick = {},
            onDarkModeToggle = {},
            onLoginClick = {},
            isLoggedIn = true,
            userName = "Bob",
            isConnected = true,
            deviceName = "ELM327"
        )
    }
}
