package com.carsense.features.dtc.presentation.screen

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carsense.features.dtc.presentation.viewmodel.DTCViewModel
import com.carsense.features.welcome.presentation.components.BackButton
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PlugZap
import com.composables.icons.lucide.Trash
import com.composables.icons.lucide.TriangleAlert

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DTCScreen(
    onBackPressed: () -> Unit,
    onErrorClick: (String) -> Unit,
    viewModel: DTCViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background, topBar = {
            TopAppBar(
                title = { Text(text = "Error Memory") }, navigationIcon = {
                BackButton(onClick = {
                    onBackPressed()
                })
            }, modifier = Modifier.statusBarsPadding()
            )
        }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Loading indicator
            if (state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
                    Text(
                        text = "Scanning for error codes...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Connection Error View
            if (state.error?.contains("Not connected") == true) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Lucide.PlugZap,
                        contentDescription = "Connection Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .scale(2f)
                            .padding(bottom = 24.dp)
                    )

                    Text(
                        text = "Connection Lost",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = state.error ?: "Please reconnect to your vehicle",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onBackPressed() }, colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Back to Dashboard") }
                }
            }
            // Regular Error message
            else if (state.error != null && !state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Check if this is the "no DTCs" special case
                    if (state.error?.startsWith("NO_DTCS:") == true) {
                        // Show check mark icon for no DTCs
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "No DTCs",
                            tint = Color.Green,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(bottom = 16.dp)
                        )

                        Text(
                            text = state.error?.substringAfter("NO_DTCS:") ?: "",
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Show retry button for no DTCs
                        Button(
                            onClick = { viewModel.loadDTCErrors() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Again",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(text = "Scan Again")
                        }
                    } else {
                        // Standard error display
                        Icon(
                            imageVector = Lucide.TriangleAlert,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .size(64.dp)
                                .padding(bottom = 16.dp)
                        )

                        Text(
                            text = state.error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Show retry button for regular errors
                        Button(
                            onClick = { viewModel.loadDTCErrors() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(text = "Try Again")
                        }
                    }
                }
            }

            // Content when not loading and no errors
            if (!state.isLoading && state.error == null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Show scan button if no errors found yet
                    if (state.dtcErrors.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = { viewModel.loadDTCErrors() },
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(text = "Scan for Error Codes")
                            }
                        }
                    } else {
                        // Card with background for the list
                        Card(
                            modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ), shape = RoundedCornerShape(12.dp)
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(state.dtcErrors) { error ->
                                    ErrorItem(
                                        code = error.code,
                                        description = error.description,
                                        onClick = { onErrorClick(error.code) })
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bottom section with error counter and clear button
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Error counter
                                Text(
                                    text = "${state.dtcErrors.size} Errors Found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error, // Dark red color
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp, vertical = 8.dp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.clearDTCErrors() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            enabled = state.dtcErrors.isNotEmpty() && !state.isLoading
                        ) {
                            Icon(
                                imageVector = Lucide.Trash,
                                contentDescription = "Clear DTC",
                                modifier = Modifier
                                    .scale(0.8f)
                                    .padding(end = 4.dp)
                            )
                            Text(
                                text = "Clear DTC",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Scan again button
                        Button(
                            onClick = { viewModel.loadDTCErrors() },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan Again",
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = "Scan Again",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorItem(code: String, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = code,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "View Details",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
