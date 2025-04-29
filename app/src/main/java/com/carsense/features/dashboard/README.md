# Dashboard Feature

This feature serves as the main navigation hub after connecting to a vehicle, providing access to
various diagnostic features.

## Structure

### Domain Layer

- **model**: Domain models
    - `VehicleInfo.kt`: Domain representation of vehicle information
- **usecase**: Use cases related to dashboard functionality
    - `GetVehicleInfoUseCase.kt`: Retrieves vehicle information

### Presentation Layer

- **intent**: User actions
    - `DashboardIntent.kt`: Defines all user actions for the dashboard
- **model**: UI state
    - `DashboardState.kt`: UI state for the dashboard
- **screen**: UI components
    - `DashboardScreen.kt`: Main dashboard screen with navigation buttons
    - **components**: Dashboard-specific UI components
        - `DashboardButton.kt`: Styled button for feature navigation
        - `ConnectionStatus.kt`: Component to show connection status
        - `VehicleInfoCard.kt`: Card displaying vehicle information
- **viewmodel**: Presentation logic
    - `DashboardViewModel.kt`: Handles dashboard state and navigation

## Navigation Options

The dashboard provides navigation to specialized diagnostic screens including:

1. **Live Gauges**: Analog gauge visualization of real-time sensor data
2. **Sensor Values**: Tabular display of all sensor readings
3. **Trouble Codes**: Diagnostic trouble code reading and clearing
4. **Performance**: Performance metrics and tests
5. **OBD2 Console**: Direct command console for advanced users
6. **Settings**: Application preferences and settings

## Usage

```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    navController: NavController
) {
    val state by viewModel.state.collectAsState()
    
    // Dashboard UI implementation
    
    // Example of navigation to a feature
    DashboardButton(
        icon = Icons.Default.Speed,
        text = "Live Gauges",
        onClick = { navController.navigate("obd2/gauges") }
    )
}
```

## Responsibilities

The Dashboard feature is responsible for:

1. Displaying high-level vehicle information
2. Providing navigation to specialized diagnostic screens
3. Showing connection status
4. Managing the main user flow after connection is established 