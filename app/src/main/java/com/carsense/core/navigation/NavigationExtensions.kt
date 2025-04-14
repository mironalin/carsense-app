package com.carsense.core.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/** Extension function to add a route with optimized Android-like transitions */
fun NavGraphBuilder.animatedComposable(
    route: String, content: @Composable (AnimatedContentScope.(NavBackStackEntry) -> Unit)
) {
    composable(
        route = route,
        enterTransition = NavigationTransitions.enterTransition,
        exitTransition = NavigationTransitions.exitTransition,
        popEnterTransition = NavigationTransitions.popEnterTransition,
        popExitTransition = NavigationTransitions.popExitTransition,
        content = content
    )
}

/**
 * Navigate to a destination, clearing the back stack Uses optimized pattern to avoid animation jank
 */
fun NavController.navigateAndClearBackStack(route: String) {
    navigate(route) {
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        popUpTo(graph.findStartDestination().id) { inclusive = true }
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
        // Restore state when reselecting a previously selected item
        restoreState = true
    }
}

/** Navigate to a screen without maintaining multiple copies in backstack */
fun NavController.navigateSingleTop(route: String) {
    navigate(route) {
        // Avoid multiple copies of the same destination
        launchSingleTop = true
        // Restore state if we're returning to a previously visited screen
        restoreState = true
    }
}
