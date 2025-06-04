package com.carsense.features.diagnostics.presentation.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carsense.features.bluetooth.presentation.intent.BluetoothIntent
import com.carsense.features.bluetooth.presentation.viewmodel.BluetoothViewModel
import com.carsense.core.navigation.NavRoutes
import com.carsense.core.navigation.LocalNavController
import com.carsense.core.navigation.navigateSingleTop

/**
 * Screen for entering vehicle mileage after successful connection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MileageInputScreen(
    onBackPressed: () -> Unit,
    viewModel: BluetoothViewModel = hiltViewModel()
) {
    var mileageText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val navController = LocalNavController.current
    val context = LocalContext.current

    // Navigate back to welcome screen after successful diagnostic creation
    LaunchedEffect(state.diagnosticUuid, state.diagnosticCreationInProgress) {
        if (state.diagnosticUuid != null && !state.diagnosticCreationInProgress) {
            // Show success toast
            Toast.makeText(
                context,
                "Diagnostic record created successfully",
                Toast.LENGTH_SHORT
            ).show()

            // Navigate back to welcome screen
            navController.navigateSingleTop(NavRoutes.WELCOME)
        }
    }

    // Prevent back navigation unless explicit
    BackHandler { /* Do nothing to prevent accidental back */ }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter Vehicle Mileage") }
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (state.isConnecting) {
                // Show connecting status
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connecting to OBD2 device...")
                }
            } else if (state.diagnosticCreationInProgress) {
                // Show progress indicator when creating diagnostic
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Creating diagnostic record...")
                }
            } else if (state.errorMessage != null) {
                // Show error message
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Connection Error",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = state.errorMessage ?: "Unknown error",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            viewModel.processIntent(BluetoothIntent.DismissError)
                            onBackPressed()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Go Back")
                    }
                }
            } else if (!state.isConnected) {
                // Connection failed or disconnected
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Connection Failed",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = onBackPressed,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Go Back")
                    }
                }
            } else {
                // Show input form when successfully connected
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Please enter your vehicle's current mileage",
                        style = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = mileageText,
                        onValueChange = {
                            mileageText = it.filter { char -> char.isDigit() }
                            isError = false
                        },
                        label = { Text("Current Mileage") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = isError,
                        supportingText = if (isError) {
                            { Text("Please enter a valid mileage") }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val odometer = mileageText.toIntOrNull()
                            if (odometer != null && odometer > 0) {
                                // Submit odometer reading and create diagnostic
                                viewModel.processIntent(
                                    BluetoothIntent.SubmitOdometerReading(
                                        odometer
                                    )
                                )
                            } else {
                                isError = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Submit")
                    }
                }
            }
        }
    }
} 