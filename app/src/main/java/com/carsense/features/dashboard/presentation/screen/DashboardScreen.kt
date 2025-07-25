package com.carsense.features.dashboard.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carsense.features.bluetooth.presentation.model.BluetoothState
import com.carsense.features.dashboard.presentation.screen.components.ConnectionStatusCard
import com.carsense.features.dashboard.presentation.screen.components.LocationFeatureCard
import com.carsense.features.dashboard.presentation.screen.components.PrimaryFeatureCard
import com.carsense.features.dashboard.presentation.screen.components.SecondaryFeatureCard
import com.composables.icons.lucide.ChartBar
import com.composables.icons.lucide.CircleOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Sun
import com.composables.icons.lucide.Monitor
import com.composables.icons.lucide.TriangleAlert
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.carsense.core.theme.ThemeManager
import com.carsense.core.theme.ThemeMode
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ThemeManagerEntryPoint {
    fun themeManager(): ThemeManager
}

/** Dashboard screen displaying vehicle diagnostic features with modern Material 3 design */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: BluetoothState,
    onDisconnect: () -> Unit,
    onSendCommand: (String) -> Unit,
    navigateToDTC: () -> Unit = {},
    navigateToSensors: () -> Unit = {},
    navigateToAnalogGauges: () -> Unit = {},
    navigateToLocation: () -> Unit = {}
) {
    val context = LocalContext.current
    val themeManager = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThemeManagerEntryPoint::class.java
        ).themeManager()
    }
    
    val coroutineScope = rememberCoroutineScope()
    val currentThemeMode by themeManager.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "CarSense",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            NavigationBar {
                // Theme toggle button
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = when (currentThemeMode) {
                                ThemeMode.SYSTEM -> Lucide.Monitor
                                ThemeMode.LIGHT -> Lucide.Sun
                                ThemeMode.DARK -> Lucide.Moon
                            },
                            contentDescription = when (currentThemeMode) {
                                ThemeMode.SYSTEM -> "System Theme (tap to switch to Light)"
                                ThemeMode.LIGHT -> "Light Theme (tap to switch to Dark)"
                                ThemeMode.DARK -> "Dark Theme (tap to switch to System)"
                            }
                        )
                    },
                    selected = currentThemeMode != ThemeMode.SYSTEM,
                    onClick = {
                        coroutineScope.launch {
                            val newTheme = when (currentThemeMode) {
                                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.SYSTEM
                            }
                            themeManager.setThemeMode(newTheme)
                        }
                    }
                )

                // Disconnect button
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = Lucide.CircleOff,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    selected = false,
                    onClick = onDisconnect
                )

                // Settings button
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = Lucide.Settings,
                            contentDescription = "Settings"
                        )
                    },
                    selected = false,
                    onClick = { /* Open settings */ }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = paddingValues.calculateBottomPadding() + 8.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Status Header
            ConnectionStatusCard(state = state)

            // Primary feature - Takes significant space
            PrimaryFeatureCard(
                title = "Analog Gauges",
                subtitle = "Real-time dashboard",
                description = "View RPM, speed, and engine data",
                icon = Icons.Default.Speed,
                onClick = navigateToAnalogGauges,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(0.4f)
            )

            // Secondary features - Split remaining space
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.35f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error Memory
                SecondaryFeatureCard(
                    title = "Error Memory",
                    subtitle = "Diagnostic codes",
                    icon = Lucide.TriangleAlert,
                    onClick = navigateToDTC,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )

                // Sensor Values
                SecondaryFeatureCard(
                    title = "Sensor Values",
                    subtitle = "Live monitoring",
                    icon = Lucide.ChartBar,
                    onClick = navigateToSensors,
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            // Location - Bottom section
            LocationFeatureCard(
                title = "Location Tracking",
                subtitle = "GPS tracking and route history",
                icon = Lucide.MapPin,
                onClick = navigateToLocation,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.25f),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
