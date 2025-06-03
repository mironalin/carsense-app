# Location Feature

This feature handles device location tracking and management in the CarSense app. It follows clean
architecture principles with a feature-based organization.

## Structure

```
features/location/
├── data/
│   ├── LocationTracker.kt             # Manages location tracking based on Bluetooth connectivity
│   ├── service/
│   │   ├── LocationService.kt         # Interface defining location service capabilities
│   │   ├── AndroidLocationService.kt  # Implementation using FusedLocationProviderClient
│   │   └── ForegroundLocationService.kt # Background service for tracking location
│   └── repository/                    # Location data repositories (future)
├── domain/
│   ├── model/
│   │   └── LocationPoint.kt           # Domain model for location data
│   └── usecase/                       # Location-related use cases
├── di/
│   └── LocationModule.kt              # Dependency injection for location components
└── presentation/
    ├── screen/
    │   └── LocationScreen.kt          # UI for location visualization
    └── viewmodel/
        └── LocationViewModel.kt       # ViewModel for location screens
```

## Integration with Bluetooth

The location tracking is integrated with the Bluetooth connection state:

- Location tracking starts automatically when a Bluetooth connection is established
- Tracking stops when Bluetooth is disconnected
- Location updates are collected every 2 seconds for optimal tracking precision

## Migration Plan

The old `core/location/LocationService.kt` implementation has been replaced with this feature-based
architecture. All migration steps are now complete:

1. ✅ Created a clean architecture structure in the location feature
2. ✅ Implemented `LocationService` interface and `AndroidLocationService` implementation
3. ✅ Added `LocationTracker` to manage tracking based on Bluetooth connectivity
4. ✅ Integrated with BluetoothViewModel to start/stop tracking based on connection state
5. ✅ Created ForegroundLocationService as a replacement for the old core implementation
6. ✅ Updated MainActivity to use the new location feature implementation
7. ✅ Removed the old core/location/LocationService.kt implementation
8. ✅ Removed core/di/LocationModule.kt to fix duplicate DI bindings

## Usage

```kotlin
// Example: Inject the LocationTracker where needed
@Inject
lateinit var locationTracker: LocationTracker

// Start location tracking (automatically connected to Bluetooth state)
// Called from BluetoothViewModel when Bluetooth connects
locationTracker.onBluetoothConnected(vehicle)

// Stop location tracking
// Called from BluetoothViewModel when Bluetooth disconnects
locationTracker.onBluetoothDisconnected()

// Release resources when no longer needed
locationTracker.release()
``` 