package com.carsense.features.welcome.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.carsense.core.model.Vehicle
import com.carsense.features.welcome.presentation.components.BackButton
import com.carsense.features.welcome.presentation.viewmodel.VehicleSelectionEvent
import com.carsense.features.welcome.presentation.viewmodel.VehicleSelectionViewModel
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(
    onBackPressed: () -> Unit,
    onVehicleAdded: () -> Unit,
    viewModel: VehicleSelectionViewModel = hiltViewModel()
) {
    var make by rememberSaveable { mutableStateOf("") }
    var model by rememberSaveable { mutableStateOf("") }
    var yearText by rememberSaveable { mutableStateOf("") }
    var vin by rememberSaveable { mutableStateOf("") }
    var nickname by rememberSaveable { mutableStateOf("") }
    var licensePlate by rememberSaveable { mutableStateOf("") }
    var engineType by rememberSaveable { mutableStateOf("") }
    var fuelType by rememberSaveable { mutableStateOf("") }
    var transmissionType by rememberSaveable { mutableStateOf("") }
    var drivetrain by rememberSaveable { mutableStateOf("") }

    // Screen initialization state that persists across recompositions
    var isInitialized by rememberSaveable { mutableStateOf(false) }

    // Add content visibility control like YourVehiclesScreen
    var showContent by rememberSaveable { mutableStateOf(false) }

    // Track if a vehicle is being added in this session
    var isAddingVehicleInThisSession by rememberSaveable { mutableStateOf(false) }

    // Store initial selected vehicle UUID to detect changes
    var initialSelectedVehicleUuid by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Collect state
    val state by viewModel.state.collectAsState()

    // First-time initialization and capture initial selected vehicle
    LaunchedEffect(Unit) {
        if (!isInitialized) {
            Timber.d("AddVehicleScreen: Starting initialization with animation sequence")

            // First pause data loading to prevent immediate state changes
            viewModel.pauseDataLoading()

            // Short delay to let the screen appear and animation start
            delay(250)

            // Store the initial selected vehicle UUID
            initialSelectedVehicleUuid = state.selectedVehicle?.uuid
            Timber.d("AddVehicleScreen: Initial selected vehicle UUID: $initialSelectedVehicleUuid")

            isInitialized = true

            // Start loading data after animation should be complete
            viewModel.resumeDataLoading()

            // Show content after data loading has started
            showContent = true

            Timber.d("AddVehicleScreen: Initialization complete, screen ready")
        }
    }

    // Monitor lifecycle events
    DisposableEffect(lifecycleOwner) {
        Timber.d("AddVehicleScreen: Setting up lifecycle observer")
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    Timber.d("AddVehicleScreen: ON_CREATE")
                }

                Lifecycle.Event.ON_START -> {
                    Timber.d("AddVehicleScreen: ON_START")
                }

                Lifecycle.Event.ON_RESUME -> {
                    Timber.d("AddVehicleScreen: ON_RESUME")
                    // Only resume data loading if screen is initialized
                    if (isInitialized) {
                        viewModel.resumeDataLoading()
                    }
                }

                Lifecycle.Event.ON_PAUSE -> {
                    Timber.d("AddVehicleScreen: ON_PAUSE - Pausing data loading")
                    viewModel.pauseDataLoading()
                }

                Lifecycle.Event.ON_STOP -> {
                    Timber.d("AddVehicleScreen: ON_STOP")
                }

                Lifecycle.Event.ON_DESTROY -> {
                    Timber.d("AddVehicleScreen: ON_DESTROY")
                }

                else -> { /* ignore other events */
                }
            }
        }

        // Add the observer
        lifecycleOwner.lifecycle.addObserver(observer)

        // Clean up on dispose
        onDispose {
            Timber.d("AddVehicleScreen: Disposing - Removing lifecycle observer")
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pauseDataLoading()
        }
    }

    val isFormValid = remember(make, model, yearText) {
        make.isNotBlank() && model.isNotBlank() && yearText.isNotBlank() && yearText.toIntOrNull() != null && yearText.toIntOrNull() in 1900..2100
    }

    // Handle successful vehicle addition
    LaunchedEffect(state.isAddingVehicle, state.selectedVehicle) {
        // Get the current selected vehicle UUID safely
        val currentSelectedVehicleUuid = state.selectedVehicle?.uuid

        // Only navigate back if:
        // 1. We were actually adding a vehicle in this session
        // 2. The adding process has completed
        // 3. The selected vehicle is different from the initial one
        if (isAddingVehicleInThisSession && !state.isAddingVehicle && currentSelectedVehicleUuid != null && currentSelectedVehicleUuid != initialSelectedVehicleUuid) {

            Timber.d(
                "AddVehicleScreen: Vehicle added successfully, navigating back. Initial UUID: $initialSelectedVehicleUuid, New UUID: $currentSelectedVehicleUuid"
            )

            // Reset the flag
            isAddingVehicleInThisSession = false

            // Pause data loading before navigation
            viewModel.pauseDataLoading()
            onVehicleAdded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Vehicle") }, navigationIcon = {
                    BackButton(onClick = {
                        Timber.d("AddVehicleScreen: Back button pressed, pausing data loading")
                        viewModel.pauseDataLoading() // Pause data loading before navigation
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
            // Show loading indicator while content isn't ready
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
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.error != null) {
                        Text(
                            text = state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                    }

                    // Form fields
                    OutlinedTextField(
                        value = make,
                        onValueChange = { make = it },
                        label = { Text("Make*") },
                        placeholder = { Text("e.g., Toyota") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model*") },
                        placeholder = { Text("e.g., Corolla") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { yearText = it },
                        label = { Text("Year*") },
                        placeholder = { Text("e.g., 2020") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number, imeAction = ImeAction.Next
                        ),
                        isError = yearText.isNotEmpty() && (yearText.toIntOrNull() == null || yearText.toIntOrNull() !in 1900..2100)
                    )

                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it },
                        label = { Text("Nickname") },
                        placeholder = { Text("e.g., My Car") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = licensePlate,
                        onValueChange = { licensePlate = it },
                        label = { Text("License Plate") },
                        placeholder = { Text("e.g., ABC123") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = vin,
                        onValueChange = { vin = it },
                        label = { Text("VIN") },
                        placeholder = { Text("Vehicle Identification Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = engineType,
                        onValueChange = { engineType = it },
                        label = { Text("Engine Type") },
                        placeholder = { Text("e.g., V6, I4") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = fuelType,
                        onValueChange = { fuelType = it },
                        label = { Text("Fuel Type") },
                        placeholder = { Text("e.g., Gasoline, Diesel, Electric") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = transmissionType,
                        onValueChange = { transmissionType = it },
                        label = { Text("Transmission Type") },
                        placeholder = { Text("e.g., Automatic, Manual") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = drivetrain,
                        onValueChange = { drivetrain = it },
                        label = { Text("Drivetrain") },
                        placeholder = { Text("e.g., FWD, RWD, AWD, 4WD") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val year = yearText.toIntOrNull() ?: 0
                            val vehicle = Vehicle(
                                make = make,
                                model = model,
                                year = year,
                                vin = vin.ifEmpty { UUID.randomUUID().toString().substring(0, 17) },
                                nickname = nickname,
                                licensePlate = licensePlate,
                                engineType = engineType.ifEmpty { "Unknown" },
                                fuelType = fuelType.ifEmpty { "Unknown" },
                                transmissionType = transmissionType.ifEmpty { "Unknown" },
                                drivetrain = drivetrain.ifEmpty { "Unknown" })

                            // Set the flag to indicate we're adding a vehicle in this session
                            isAddingVehicleInThisSession = true
                            Timber.d("AddVehicleScreen: Adding vehicle, setting isAddingVehicleInThisSession = true")

                            viewModel.onEvent(VehicleSelectionEvent.AddNewVehicle(vehicle))
                        },
                        enabled = isFormValid && !state.isAddingVehicle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        if (state.isAddingVehicle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Add Vehicle")
                        }
                    }

                    Text(
                        text = "* Required fields",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
} 