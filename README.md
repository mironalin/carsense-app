# CarSense

An Android application for OBD2 car diagnostics via Bluetooth.

## Overview

CarSense connects to your vehicle's OBD2 port using a Bluetooth adapter (ELM327 compatible) and provides detailed diagnostic information about your vehicle's status. The app supports reading sensor data, diagnostic trouble codes, and performing various diagnostic tests.

## Features

- Bluetooth connectivity to OBD2 adapters
- Live sensor data visualization with analog gauges
- Diagnostic trouble code (DTC) reading and clearing
- Performance metrics and tests
- Direct OBD2 command console
- Settings and customization

## Architecture

This app is built using:
- **Clean Architecture** with feature-based organization
- **MVI** (Model-View-Intent) pattern for UI and state management
- **Jetpack Compose** for modern, declarative UI
- **Hilt** for dependency injection
- **Kotlin Coroutines & Flow** for reactive programming

## Project Structure

```
com.carsense/
├── core/         # Core application components
├── ui/           # UI components and theme
└── features/     # Feature modules 
    ├── bluetooth/  # Bluetooth connectivity
    ├── dashboard/  # Main dashboard
    ├── obd2/       # OBD2 diagnostics
    └── settings/   # App settings
```

Each feature is organized following clean architecture principles with its own data, domain, and presentation layers.

## Setup & Development

1. Clone the repository
2. Open in Android Studio
3. Build and run on your Android device

Note: This app requires an Android device with Bluetooth capabilities and an OBD2 Bluetooth adapter connected to your vehicle. 