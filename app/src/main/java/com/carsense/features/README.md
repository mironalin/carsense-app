# Features

This directory contains feature modules organized according to clean architecture principles.

## Feature Organization

Each feature is organized into its own module with the following structure:

```
feature/
├── data/          # Data sources, repositories implementation
├── domain/        # Business logic, domain models, interfaces
└── presentation/  # UI components, ViewModels, states, intents
```

## Features

### Bluetooth
Handles Bluetooth device scanning, pairing, and communication with OBD2 adapters.

### Dashboard
Serves as the main navigation hub after connecting to a vehicle, providing access to different diagnostic features.

### OBD2
Contains OBD2 diagnostic capabilities including:
- Live sensor data visualization
- Analog gauges
- Diagnostic trouble code reading and clearing
- Performance metrics
- Direct command console

### Settings
Manages application settings and preferences.

## MVI Architecture

Each feature follows the Model-View-Intent (MVI) pattern:

- **Model**: State classes in the presentation/model package
- **View**: Compose UI in the presentation/screen package
- **Intent**: User actions defined in the presentation/intent package
- **ViewModel**: Logic for processing intents in the presentation/viewmodel package

## Feature Isolation

Features should minimize dependencies on other features. Inter-feature communication should be handled through:

1. Navigation (for UI flows)
2. Shared domain interfaces (for business logic)
3. The core module (for shared utilities)

This approach ensures that features can be developed, tested, and maintained independently. 