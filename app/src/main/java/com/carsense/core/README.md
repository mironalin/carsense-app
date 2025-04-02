# Core Module

This module contains core application components that are shared across all features.

## Contents

### Application
The base Application class and application-level initialization.

### Base
Base classes for the MVI architecture:
- `BaseViewModel.kt`: Base class for all ViewModels with common functionality
- `BaseIntent.kt`: Base interface for all intents
- `BaseState.kt`: Base interface for all state models

### DI (Dependency Injection)
Hilt modules for providing dependencies:
- `AppModule.kt`: Application-level dependencies
- `BluetoothModule.kt`: Bluetooth-related dependencies
- `OBD2Module.kt`: OBD2-related dependencies

### Navigation
Navigation-related components:
- `AppNavigation.kt`: Main navigation setup
- `Routes.kt`: Route constants for navigation

### Utils
Utility classes and helper functions:
- `Logger.kt`: Logging utilities
- `Extensions.kt`: Kotlin extension functions

### Constants
Application-wide constants:
- `OBD2Constants.kt`: Constants related to OBD2 protocols and commands
- `BluetoothConstants.kt`: Constants for Bluetooth connectivity

## Usage

The core module should only contain code that is shared across multiple features. Feature-specific code should remain within the respective feature modules.

When creating a new feature, extend from the base classes provided here to maintain consistency throughout the application. 