# UI Module

This module contains shared UI components and theme elements that are used throughout the application.

## Contents

### MainActivity
The main activity and entry point for the application, which hosts the navigation components.

### Theme
UI styling and theming components:
- `Theme.kt`: Application theme definition
- `Color.kt`: Color palette
- `Type.kt`: Typography definitions
- `Shape.kt`: Shape definitions

### Components
Reusable UI components:
- `MessageCard.kt`: Component for displaying messages
- `CommandChip.kt`: Chip component for OBD2 commands
- `LoadingIndicator.kt`: Component for displaying loading state
- `ErrorDialog.kt`: Dialog for showing errors
- `StatusIndicator.kt`: Component for displaying connection status

## Usage

When creating UI components, consider:

1. **Reusability**: If a component is used in multiple features, it should be placed here
2. **Consistency**: Follow the design system defined in the theme package
3. **Composition**: Create small, composable functions that can be combined

UI components in this directory should be feature-agnostic. Feature-specific UI components should remain within their respective feature modules.

Example:
```kotlin
@Composable
fun StatusIndicator(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(12.dp)
            .background(
                color = if (isConnected) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error,
                shape = CircleShape
            )
    )
}
``` 