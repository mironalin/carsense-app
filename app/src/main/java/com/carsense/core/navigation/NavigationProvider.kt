package com.carsense.core.navigation

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavController

/**
 * CompositionLocal to provide NavController across the app
 */
val LocalNavController = compositionLocalOf<NavController> {
    error("No NavController provided")
} 