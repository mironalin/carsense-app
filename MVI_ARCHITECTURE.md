# MVI Architecture in CarSense App

This document describes the Model-View-Intent (MVI) architecture implemented in the CarSense app.

## Overview

Model-View-Intent (MVI) is a unidirectional data flow architecture pattern that provides a clear and predictable way to manage state in Android applications.

The core components are:

1. **Model**: The state of the UI
2. **View**: The UI components (Jetpack Compose)
3. **Intent**: User actions that trigger state changes

## Implementation

### 1. Model (State)

The state is represented by immutable data classes:

```kotlin
data class BluetoothUiState(
    val scannedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val pairedDevices: List<BluetoothDeviceDomain> = emptyList(),
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val errorMessage: String? = null,
    val messages: List<MessageDisplay> = emptyList()
)
```

### 2. Intent

User actions are represented as sealed classes:

```kotlin
sealed class BluetoothIntent {
    data class ConnectToDevice(val device: BluetoothDeviceDomain) : BluetoothIntent()
    object DisconnectFromDevice : BluetoothIntent()
    object StartScan : BluetoothIntent()
    object StopScan : BluetoothIntent()
    data class SendCommand(val message: String) : BluetoothIntent()
    object WaitForConnections : BluetoothIntent()
    object DismissError : BluetoothIntent()
}
```

### 3. ViewModel (Intent Processor)

The ViewModel processes intents and updates the state:

```kotlin
fun processIntent(intent: BluetoothIntent) {
    // First update state through the reducer
    _state.update { currentState -> reduce(currentState, intent) }

    // Then handle side effects
    when (intent) {
        is BluetoothIntent.ConnectToDevice -> connectToDevice(intent.device)
        is BluetoothIntent.DisconnectFromDevice -> disconnectFromDevice()
        is BluetoothIntent.StartScan -> startScan()
        // ...
    }
}

private fun reduce(currentState: BluetoothUiState, intent: BluetoothIntent): BluetoothUiState {
    return when (intent) {
        is BluetoothIntent.ConnectToDevice -> currentState.copy(isConnecting = true)
        is BluetoothIntent.DisconnectFromDevice -> currentState.copy(isConnecting = false, isConnected = false)
        // ...
    }
}
```

### 4. View (Compose UI)

The UI observes the state and dispatches intents:

```kotlin
@Composable
fun MainScreen(viewModel: BluetoothViewModel) {
    val state by viewModel.state.collectAsState()

    // UI based on state
    when {
        state.isConnecting -> {
            // Show connecting UI
        }
        state.isConnected -> {
            OBD2Screen(
                state = state,
                onDisconnect = { viewModel.processIntent(BluetoothIntent.DisconnectFromDevice) },
                onSendCommand = { message -> viewModel.processIntent(BluetoothIntent.SendCommand(message)) }
            )
        }
        // ...
    }
}
```

## Benefits of MVI

- **Single Source of Truth**: The state is the single source of truth for the UI
- **Unidirectional Data Flow**: Clear flow from user action to state change
- **Immutability**: State is immutable, making it easier to reason about
- **Predictable State Updates**: State changes are predictable and traceable
- **Testability**: Each component can be tested in isolation

## Flow Diagram

```
User Action → Intent → ViewModel (processIntent) → Reducer (reduce) → New State → UI Update
```

## Future Improvements

1. **Side Effect Handling**: Implement a dedicated side effect system
2. **State Persistence**: Save and restore state across app launches
3. **Feature-Based Modules**: Organize code by features instead of layers
4. **Testing**: Add comprehensive unit and UI tests

## Conclusion

The MVI architecture provides a clean, predictable way to manage state in the CarSense app. By clearly separating concerns and following a unidirectional data flow, the app is more maintainable and testable.