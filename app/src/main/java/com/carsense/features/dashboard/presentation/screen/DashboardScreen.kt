package com.carsense.features.dashboard.presentation.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.carsense.features.bluetooth.presentation.model.BluetoothState
import com.composables.icons.lucide.ChartBar
import com.composables.icons.lucide.CircleOff
import com.composables.icons.lucide.Disc
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Moon
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Wrench

/** Dashboard screen displaying a grid of vehicle diagnostic features */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: BluetoothState,
    onDisconnect: () -> Unit,
    onSendCommand: (String) -> Unit,
    navigateToDTC: () -> Unit = {}
) {
    // Use explicit primary color for icons to ensure brand consistency
    val iconColor = MaterialTheme.colorScheme.primary

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
                // Dark mode toggle
                NavigationBarItem(
                    icon = {
                        Icon(imageVector = Lucide.Moon, contentDescription = "Dark Mode")
                    },
                    selected = false,
                    onClick = { /* Toggle dark mode */ }
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
                        Icon(imageVector = Lucide.Settings, contentDescription = "Settings")
                    },
                    selected = false,
                    onClick = { /* Open settings */ }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // First row of feature cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Dashboard card
                FeatureCard(
                    title = "Dashboard",
                    onClick = { onSendCommand("010C") }, // RPM command
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    iconColor = iconColor
                ) {
                    Icon(
                        imageVector = Lucide.Gauge,
                        contentDescription = "Dashboard",
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Error Memory card
                FeatureCard(
                    title = "Error Memory",
                    onClick = {
                        navigateToDTC()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    iconColor = iconColor
                ) {
                    Icon(
                        imageVector = Lucide.TriangleAlert,
                        contentDescription = "Error Memory",
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Second row of feature cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sensor Values card
                FeatureCard(
                    title = "Sensor Values",
                    onClick = { /* Open sensor values screen */ },
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    iconColor = iconColor
                ) {
                    Icon(
                        imageVector = Lucide.ChartBar,
                        contentDescription = "Sensor Values",
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Service Reset card
                FeatureCard(
                    title = "Service Reset",
                    onClick = { /* Open service reset screen */ },
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    iconColor = iconColor
                ) {
                    Icon(
                        imageVector = Lucide.Wrench,
                        contentDescription = "Service Reset",
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Third row of feature cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // DPF card
                FeatureCard(
                    title = "DPF",
                    onClick = { /* Open DPF screen */ },
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    iconColor = iconColor
                ) {
                    Icon(
                        imageVector = Lucide.Disc,
                        contentDescription = "DPF",
                        modifier = Modifier.size(64.dp)
                    )
                }

                // Parking Brake card
                FeatureCard(
                    title = "Parking Brake",
                    onClick = { /* Open parking brake screen */ },
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    iconColor = iconColor
                ) {
                    Icon(
                        imageVector = Lucide.Target,
                        contentDescription = "Parking Brake",
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon content with explicit tint
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides iconColor
                ) { content() }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Feature title
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}
