package com.carsense.features.welcome.presentation.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.carsense.core.model.Vehicle
import com.carsense.core.model.VehicleBrandsAndModels
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
    var licensePlate by rememberSaveable { mutableStateOf("") }
    var engineType by rememberSaveable { mutableStateOf("") }
    var fuelType by rememberSaveable { mutableStateOf("") }
    var transmissionType by rememberSaveable { mutableStateOf("") }
    var drivetrain by rememberSaveable { mutableStateOf("") }

    // Get available models for the selected make
    val availableModels = remember(make) {
        VehicleBrandsAndModels.getModelsForMake(make)
    }

    // Expanded states for dropdowns
    var makeExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var fuelTypeExpanded by remember { mutableStateOf(false) }
    var transmissionExpanded by remember { mutableStateOf(false) }
    var drivetrainExpanded by remember { mutableStateOf(false) }

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

    // Helper function for consistent dropdown styling
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun StyledDropdownField(
        value: String,
        onValueChange: (String) -> Unit,
        label: @Composable () -> Unit,
        placeholder: @Composable () -> Unit,
        expanded: Boolean,
        onExpandedChange: () -> Unit,
        onDismissRequest: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        isError: Boolean = false,
        supportingText: @Composable (() -> Unit)? = null,
        options: List<String>,
        onOptionSelected: (String) -> Unit
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) onExpandedChange() },
            modifier = modifier
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                readOnly = true,
                label = label,
                placeholder = placeholder,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                singleLine = true,
                isError = isError,
                enabled = enabled,
                supportingText = supportingText,
                shape = RoundedCornerShape(12.dp)
            )

            if (enabled) {
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = onDismissRequest,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .defaultMinSize(minWidth = 200.dp)
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (option == value) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                            onClick = {
                                onOptionSelected(option)
                                onDismissRequest()
                            },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                trailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                                disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.38f
                                ),
                                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurface.copy(
                                    alpha = 0.38f
                                )
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (option == value) MaterialTheme.colorScheme.primaryContainer.copy(
                                        alpha = 0.1f
                                    )
                                    else Color.Transparent
                                )
                        )
                    }
                }
            }
        }
    }

    // Helper function for consistent text input styling
    @Composable
    fun StyledTextField(
        value: String,
        onValueChange: (String) -> Unit,
        label: @Composable () -> Unit,
        placeholder: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        readOnly: Boolean = false,
        isError: Boolean = false,
        keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
        supportingText: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = null,
        singleLine: Boolean = true
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            placeholder = placeholder,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                disabledBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = modifier.fillMaxWidth(),
            singleLine = singleLine,
            isError = isError,
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            supportingText = supportingText,
            trailingIcon = trailingIcon,
            shape = RoundedCornerShape(12.dp)
        )
    }

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

    // Validation for all form fields
    val isMakeValid = make.isNotBlank()
    val isModelValid = model.isNotBlank()
    val isYearValid =
        yearText.isNotBlank() && yearText.toIntOrNull() != null && yearText.toIntOrNull() in 1900..2100
    val isLicensePlateValid = licensePlate.length <= 15 // Basic validation for license plate
    val isVinValid =
        vin.isEmpty() || vin.length == 17 // VIN is optional, but must be 17 chars if provided
    val isEngineTypeValid = engineType.isNotBlank()
    val isFuelTypeValid = fuelType.isNotBlank()
    val isTransmissionTypeValid = transmissionType.isNotBlank()
    val isDrivetrainValid = drivetrain.isNotBlank()

    // Overall form validity
    val isFormValid =
        isMakeValid && isModelValid && isYearValid && isLicensePlateValid && isVinValid && isEngineTypeValid && isFuelTypeValid && isTransmissionTypeValid && isDrivetrainValid

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

    // Reset model when make changes
    LaunchedEffect(make) {
        if (make.isNotBlank() && model.isNotBlank() && !availableModels.contains(model)) {
            model = ""
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

                    // Make and Model on the same row with dropdowns
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Make dropdown
                        StyledDropdownField(
                            value = make,
                            onValueChange = { make = it },
                            label = { Text("Make*") },
                            placeholder = { Text("Select make") },
                            expanded = makeExpanded,
                            onExpandedChange = { makeExpanded = !makeExpanded },
                            onDismissRequest = { makeExpanded = false },
                            modifier = Modifier.weight(1f),
                            isError = make.isNotBlank() && !isMakeValid,
                            supportingText = { if (make.isNotBlank() && !isMakeValid) Text("Required") },
                            options = VehicleBrandsAndModels.carMakes,
                            onOptionSelected = { make = it })

                        // Model dropdown (dependent on selected make)
                        StyledDropdownField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Model*") },
                            placeholder = { Text("Select model") },
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = !modelExpanded },
                            onDismissRequest = { modelExpanded = false },
                            modifier = Modifier.weight(1f),
                            isError = model.isNotBlank() && !isModelValid,
                            supportingText = { if (model.isNotBlank() && !isModelValid) Text("Required") },
                            options = availableModels,
                            onOptionSelected = { model = it },
                            enabled = make.isNotBlank()
                        )
                    }

                    // Year and License Plate on the same row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StyledTextField(
                            value = yearText,
                            onValueChange = { yearText = it },
                            label = { Text("Year*") },
                            placeholder = { Text("e.g., 2020") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number, imeAction = ImeAction.Next
                            ),
                            isError = yearText.isNotEmpty() && !isYearValid,
                            supportingText = { if (yearText.isNotEmpty() && !isYearValid) Text("Enter valid year (1900-2100)") })

                        StyledTextField(
                            value = licensePlate,
                            onValueChange = { licensePlate = it },
                            label = { Text("License Plate") },
                            placeholder = { Text("e.g., ABC123") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            isError = licensePlate.isNotEmpty() && !isLicensePlateValid,
                            supportingText = {
                                if (licensePlate.isNotEmpty() && !isLicensePlateValid) Text(
                                    "Max 15 characters"
                                )
                            })
                    }

                    // Engine Type and Fuel Type on the same row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StyledTextField(
                            value = engineType,
                            onValueChange = { engineType = it },
                            label = { Text("Engine Type*") },
                            placeholder = { Text("e.g., V6, I4") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            isError = engineType.isNotBlank() && !isEngineTypeValid,
                            supportingText = {
                                if (engineType.isNotBlank() && !isEngineTypeValid) Text(
                                    "Required"
                                )
                            })

                        // Fuel Type Dropdown
                        StyledDropdownField(
                            value = fuelType,
                            onValueChange = { fuelType = it },
                            label = { Text("Fuel Type*") },
                            placeholder = { Text("Fuel type") },
                            expanded = fuelTypeExpanded,
                            onExpandedChange = { fuelTypeExpanded = !fuelTypeExpanded },
                            onDismissRequest = { fuelTypeExpanded = false },
                            modifier = Modifier.weight(1f),
                            isError = fuelType.isNotBlank() && !isFuelTypeValid,
                            supportingText = { if (fuelType.isNotBlank() && !isFuelTypeValid) Text("Required") },
                            options = VehicleBrandsAndModels.fuelTypeOptions,
                            onOptionSelected = { fuelType = it })
                    }

                    // Transmission Type on its own row
                    StyledDropdownField(
                        value = transmissionType,
                        onValueChange = { transmissionType = it },
                        label = { Text("Transmission Type*") },
                        placeholder = { Text("Select Transmission Type") },
                        expanded = transmissionExpanded,
                        onExpandedChange = { transmissionExpanded = !transmissionExpanded },
                        onDismissRequest = { transmissionExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        isError = transmissionType.isNotBlank() && !isTransmissionTypeValid,
                        supportingText = {
                            if (transmissionType.isNotBlank() && !isTransmissionTypeValid) Text(
                                "Required"
                            )
                        },
                        options = VehicleBrandsAndModels.transmissionOptions,
                        onOptionSelected = { transmissionType = it })

                    // Drivetrain on its own row
                    StyledDropdownField(
                        value = drivetrain,
                        onValueChange = { drivetrain = it },
                        label = { Text("Drivetrain*") },
                        placeholder = { Text("Select Drivetrain") },
                        expanded = drivetrainExpanded,
                        onExpandedChange = { drivetrainExpanded = !drivetrainExpanded },
                        onDismissRequest = { drivetrainExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        isError = drivetrain.isNotBlank() && !isDrivetrainValid,
                        supportingText = { if (drivetrain.isNotBlank() && !isDrivetrainValid) Text("Required") },
                        options = VehicleBrandsAndModels.drivetrainOptions,
                        onOptionSelected = { drivetrain = it })

                    StyledTextField(
                        value = vin,
                        onValueChange = { vin = it.uppercase() },
                        label = { Text("VIN") },
                        placeholder = { Text("Vehicle Identification Number") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        isError = vin.isNotEmpty() && !isVinValid,
                        supportingText = { if (vin.isNotEmpty() && !isVinValid) Text("VIN must be 17 characters") })

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val year = yearText.toIntOrNull() ?: 0
                            val vehicle = Vehicle(
                                make = make,
                                model = model,
                                year = year,
                                vin = vin.ifEmpty { UUID.randomUUID().toString().substring(0, 17) },
                                licensePlate = licensePlate,
                                engineType = engineType,
                                fuelType = fuelType,
                                transmissionType = transmissionType,
                                drivetrain = drivetrain
                            )

                            // Set the flag to indicate we're adding a vehicle in this session
                            isAddingVehicleInThisSession = true
                            Timber.d("AddVehicleScreen: Adding vehicle, setting isAddingVehicleInThisSession = true")

                            viewModel.onEvent(VehicleSelectionEvent.AddNewVehicle(vehicle))
                        },
                        enabled = isFormValid && !state.isAddingVehicle,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                    ) {
                        if (state.isAddingVehicle) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = "Add Vehicle", style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
} 