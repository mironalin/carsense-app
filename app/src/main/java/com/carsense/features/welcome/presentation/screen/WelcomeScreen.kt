package com.carsense.features.welcome.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carsense.R
import com.carsense.features.welcome.presentation.viewmodel.WelcomeEvent
import com.carsense.features.welcome.presentation.viewmodel.WelcomeViewModel
import com.carsense.ui.theme.CarSenseTheme
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleDashed
import com.composables.icons.lucide.LayoutDashboard
import com.composables.icons.lucide.LogIn
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings
import kotlinx.coroutines.delay
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
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isLoggedIn) {
                            Text(
                                text = "Welcome, ${userName ?: "User"}!",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isConnected) Lucide.CircleCheck else Lucide.CircleDashed,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isConnected) "Connected to ${deviceName ?: "OBD2"}" else "Not connected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = "CarSense",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Your Smart Driving Companion",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = {
                                if (isLoggedIn && isConnected) {
                                    // Prevent logout when connected
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "Please disconnect from OBD2 device before logging out"
                                        )
                                    }
                                } else if (isLoggedIn && !isLogoutLoading && !isConnected) {
                                    isLogoutLoading = true
                                    scope.launch {
                                        // Simulate network delay for logout only
                                        delay(1000)
                                        onLoginClick()
                                        isLogoutLoading = false
                                    }
                                } else if (!isLoggedIn) {
                                    // Immediately proceed to login screen without loading
                                    onLoginClick()
                                }
                            },
                            enabled = !isLogoutLoading
                        ) {
                            if (isLoggedIn && isLogoutLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isLoggedIn) Lucide.LogOut else Lucide.LogIn,
                                    contentDescription = if (isLoggedIn) "Logout" else "Login/Register",
                                    tint = if (isLoggedIn && isConnected) MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.5f
                                    ) else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onSettingsClick() }) {
                        Icon(
                            imageVector = Lucide.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
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
                Image(
                    painter = painterResource(R.drawable.logo),
                    contentDescription = "CarSense Logo",
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .heightIn(max = 70.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (!isLoggedIn) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Please log in to connect to your OBD2 device")
                            }
                        } else if (isConnected) {
                            onDisconnectClick()
                        } else {
                            viewModel.onEvent(WelcomeEvent.Connect)
                            onConnectClick()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
                        text = if (isConnected) "Disconnect from ${deviceName ?: "OBD2"}" else "Connect to CarSense",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (isConnected && isLoggedIn) {
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
