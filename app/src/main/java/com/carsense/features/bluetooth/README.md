# Bluetooth Feature

This feature handles Bluetooth connectivity with OBD2 adapters, including device scanning, pairing,
connection, and communication.

## Structure

### Data Layer

- **repository**: Implementations of Bluetooth repositories
    - `BluetoothRepositoryImpl.kt`: Implements Bluetooth operations
    - `DataStoreRepositoryImpl.kt`: Stores paired device information
- **adapter**: Bluetooth adapter implementation
    - `BluetoothAdapterImpl.kt`: Wrapper for Android's BluetoothAdapter

### Domain Layer

- **model**: Domain models
    - `BluetoothDeviceDomain.kt`: Domain representation of a Bluetooth device
- **repository**: Repository interfaces
    - `BluetoothRepository.kt`: Interface for Bluetooth operations
- **usecase**: Use cases for Bluetooth operations
    - `ConnectToDeviceUseCase.kt`: Handles device connection
    - `ScanForDevicesUseCase.kt`: Scans for available devices
    - `DisconnectDeviceUseCase.kt`: Disconnects from a device

### Presentation Layer

- **intent**: User actions
    - `BluetoothIntent.kt`: Defines all user actions for Bluetooth
- **model**: UI state
    - `BluetoothState.kt`: UI state for Bluetooth screens
    - `MessageModel.kt`: Model for displaying messages
- **screen**: UI components
    - `BluetoothDeviceScreen.kt`: Screen for scanning and connecting to devices
- **viewmodel**: Presentation logic
    - `BluetoothViewModel.kt`: Handles Bluetooth operations and state

## Flow

1. User opens the app and is presented with the Bluetooth screen
2. App scans for available Bluetooth devices (OBD2 adapters)
3. User selects a device to connect to
4. Upon successful connection, the app navigates to the Dashboard
5. All communication with the OBD2 adapter happens through the Bluetooth feature

## Usage

Example of connecting to a device:

```kotlin
// In a composable
val viewModel: BluetoothViewModel = hiltViewModel()
val state by viewModel.state.collectAsState()

// When a device is clicked
Button(onClick = {
    viewModel.processIntent(BluetoothIntent.ConnectToDevice(device))
}) {
    Text("Connect")
}
``` 