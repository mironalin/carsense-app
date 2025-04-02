# Settings Feature

This feature manages application preferences and settings for the CarSense app.

## Structure

### Data Layer
- **repository**: Implementations of settings repositories
  - `SettingsRepositoryImpl.kt`: Implements settings storage and retrieval

### Domain Layer
- **model**: Domain models
  - `AppSettings.kt`: Domain representation of application settings
- **repository**: Repository interfaces
  - `SettingsRepository.kt`: Interface for settings operations
- **usecase**: Use cases related to settings
  - `GetSettingsUseCase.kt`: Retrieves saved settings
  - `UpdateSettingsUseCase.kt`: Updates application settings

### Presentation Layer
- **intent**: User actions
  - `SettingsIntent.kt`: Defines all user actions for settings
- **model**: UI state
  - `SettingsState.kt`: UI state for the settings screen
- **screen**: UI components
  - `SettingsScreen.kt`: Settings configuration screen
- **viewmodel**: Presentation logic
  - `SettingsViewModel.kt`: Handles settings operations and state

## Available Settings

The settings feature manages:

### Connectivity
- Preferred connection mode (Auto/Manual)
- Connection timeout duration
- Adapter initialization protocol

### Display
- Preferred units (Imperial/Metric)
- Theme selection (Light/Dark/System)
- Display refresh rate

### Diagnostics
- Sensor polling frequency
- Default parameters for gauge view
- DTC interpretation language
- Performance test parameters

### Data
- Data logging options
- Export format
- Storage location

## Usage

```kotlin
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    
    // Settings UI implementation
    
    // Example of updating a setting
    Switch(
        checked = state.isDarkMode,
        onCheckedChange = { enabled ->
            viewModel.processIntent(SettingsIntent.UpdateThemeMode(
                if (enabled) ThemeMode.DARK else ThemeMode.LIGHT
            ))
        }
    )
}
```

## Data Persistence

Settings are stored using DataStore Preferences:
- Serialized as protocol buffers
- Type-safe access
- Asynchronous operations with Flow
- Automatic migration from SharedPreferences 