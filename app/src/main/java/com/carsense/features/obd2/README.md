# OBD2 Feature

This feature provides comprehensive OBD2 diagnostics including sensor data visualization, trouble code reading, and direct command communication.

## Structure

### Data Layer
- **repository**: Implementations of OBD2 repositories
  - `OBD2RepositoryImpl.kt`: Implements OBD2 protocol operations
- **source**: Data sources for OBD2 information
  - `ELM327CommandExecutor.kt`: Handles communication with ELM327 adapters
- **mapper**: Data mappers
  - `OBD2ResponseMapper.kt`: Maps raw OBD2 responses to domain models

### Domain Layer
- **model**: Domain models
  - `OBD2Parameter.kt`: Represents a diagnostic parameter
  - `DiagnosticTroubleCode.kt`: Represents a trouble code
  - `SensorReading.kt`: Represents a sensor reading
- **repository**: Repository interfaces
  - `OBD2Repository.kt`: Interface for OBD2 operations
- **usecase**: Use cases for OBD2 operations
  - `GetLiveSensorDataUseCase.kt`: Retrieves live sensor data
  - `ReadTroubleCodesUseCase.kt`: Reads diagnostic trouble codes
  - `ClearTroubleCodesUseCase.kt`: Clears trouble codes
  - `SendCustomCommandUseCase.kt`: Sends custom OBD2 commands

### Presentation Layer
- **intent**: User actions for each screen
  - `SensorIntent.kt`: Intents for the sensor screen
  - `GaugeIntent.kt`: Intents for the gauge screen
  - `DTCIntent.kt`: Intents for the trouble code screen
  - `PerformanceIntent.kt`: Intents for the performance screen
  - `ConsoleIntent.kt`: Intents for the command console
- **model**: UI state for each screen
  - `SensorState.kt`: State for the sensor screen
  - `GaugeState.kt`: State for the gauge screen
  - `DTCState.kt`: State for the trouble code screen
  - `PerformanceState.kt`: State for the performance screen
  - `ConsoleState.kt`: State for the command console
- **screen**: UI components
  - `SensorScreen.kt`: Shows all sensor data in a list
  - `GaugeScreen.kt`: Shows analog gauges for selected parameters
  - `DTCScreen.kt`: Shows and manages diagnostic trouble codes
  - `PerformanceScreen.kt`: Shows performance metrics
  - `OBD2ConsoleScreen.kt`: Provides a direct command console
  - **components**: Reusable UI components for OBD2 screens
    - `Gauge.kt`: Analog gauge component
    - `SensorCard.kt`: Card for displaying a sensor reading
    - `DTCCard.kt`: Card for displaying a trouble code
    - `PerformanceMetric.kt`: Card for performance metrics
    - `CommandConsole.kt`: Console for sending commands
- **viewmodel**: Presentation logic for each screen
  - `SensorViewModel.kt`: ViewModel for the sensor screen
  - `GaugeViewModel.kt`: ViewModel for the gauge screen
  - `DTCViewModel.kt`: ViewModel for the trouble code screen
  - `PerformanceViewModel.kt`: ViewModel for the performance screen
  - `ConsoleViewModel.kt`: ViewModel for the command console

## Features

### 1. Sensor Data
- Live readout of all available sensor data
- Configurable update frequency
- Support for standard and enhanced PIDs

### 2. Gauge Visualization
- Real-time analog gauges for key parameters
- Customizable gauge layouts
- Visual indicators for normal/warning ranges

### 3. Diagnostic Trouble Codes
- Read and interpret DTCs
- Clear trouble codes
- Code descriptions and potential solutions

### 4. Performance Tests
- 0-60 acceleration test
- Engine performance metrics
- Fuel efficiency monitoring

### 5. Command Console
- Direct OBD2 command input
- Response history
- Command shortcuts for common operations

## Usage Example

```kotlin
// Reading sensor data
viewModel.processIntent(SensorIntent.RefreshSensors)

// Clearing trouble codes
viewModel.processIntent(DTCIntent.ClearTroubleCodes)

// Sending a custom command
viewModel.processIntent(ConsoleIntent.SendCommand("010C"))
```

## OBD2 Protocol Support

This feature supports:
- SAE J1850 PWM (41.6 kbaud)
- SAE J1850 VPW (10.4 kbaud)
- ISO 9141-2 (5 baud init, 10.4 kbaud)
- ISO 14230-4 KWP (5 baud init, 10.4 kbaud)
- ISO 14230-4 KWP (fast init, 10.4 kbaud)
- ISO 15765-4 CAN (11 bit ID, 500 kbaud)
- ISO 15765-4 CAN (29 bit ID, 500 kbaud)
- ISO 15765-4 CAN (11 bit ID, 250 kbaud)
- ISO 15765-4 CAN (29 bit ID, 250 kbaud) 