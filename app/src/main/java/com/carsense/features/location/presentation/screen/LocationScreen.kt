package com.carsense.features.location.presentation.screen

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carsense.core.room.entity.LocationPointEntity
import com.carsense.features.location.presentation.viewmodel.LocationViewModel
import com.carsense.features.welcome.presentation.components.BackButton
import com.composables.icons.lucide.FileJson
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MapPin
import com.composables.icons.lucide.Trash2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    viewModel: LocationViewModel = hiltViewModel(), onBackPressed: () -> Unit
) {
    // Collect the Flow<List<LocationPointEntity>> from the StateFlow
    val locationPointsFlow by viewModel.locationPoints.collectAsState()
    // Collect the actual location points from the inner Flow
    val locationPoints by locationPointsFlow.collectAsState(initial = emptyList())
    // Track if data clearing is in progress
    val isClearingData by viewModel.isClearingData.collectAsState()

    // State for confirmation dialog
    var showConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Location History") },
                navigationIcon = {
                    BackButton(onClick = {
                        onBackPressed()
                    })
                },
                actions = {
                    // Only show action buttons if we have location points
                    if (locationPoints.isNotEmpty()) {
                        // Export button
                        val exportLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.CreateDocument("application/json"),
                            onResult = { uri ->
                                if (uri != null) {
                                    val context = viewModel.viewModelContext
                                    val success = viewModel.exportLocationDataToJson(context, uri)
                                    if (success) {
                                        Toast.makeText(
                                            context,
                                            "Location data exported successfully",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Failed to export location data",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        )

                        IconButton(
                            onClick = {
                                val timestamp =
                                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                                        .format(Date())
                                exportLauncher.launch("carsense_locations_$timestamp.json")
                            }
                        ) {
                            Icon(
                                imageVector = Lucide.FileJson,
                                contentDescription = "Export location data",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Clear button
                        IconButton(
                            onClick = { showConfirmDialog = true }
                        ) {
                            Icon(
                                imageVector = Lucide.Trash2,
                                contentDescription = "Clear location data",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }) { paddingValues ->

        // Confirmation dialog
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Clear Location Data") },
                text = { Text("Are you sure you want to delete all location history? This action cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllLocationData()
                            showConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Show loading indicator when clearing data
        if (isClearingData) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (locationPoints.isEmpty()) {
            // Show empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Lucide.MapPin,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No location data available",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Location tracking will start automatically when you connect to your vehicle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // Show list of location points
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                items(locationPoints) { locationPoint ->
                    LocationPointItem(locationPoint = locationPoint)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
fun LocationPointItem(locationPoint: LocationPointEntity) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Date and time
            Text(
                text = formatTimestamp(locationPoint.timestamp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Coordinates
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Latitude",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = locationPoint.latitude.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Column {
                    Text(
                        text = "Longitude",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = locationPoint.longitude.toString(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Additional data if available
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Speed
                Column {
                    Text(
                        text = "Speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = locationPoint.speed?.let { "${it.toInt()} m/s" } ?: "N/A",
                        style = MaterialTheme.typography.bodyMedium)
                }

                // Altitude
                Column {
                    Text(
                        text = "Altitude",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = locationPoint.altitude?.let { "${it.toInt()} m" } ?: "N/A",
                        style = MaterialTheme.typography.bodyMedium)
                }

                // Accuracy
                Column {
                    Text(
                        text = "Accuracy",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = locationPoint.accuracy?.let { "${it.toInt()} m" } ?: "N/A",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
} 