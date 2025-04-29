package com.carsense.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.carsense.features.bluetooth.presentation.intent.BluetoothIntent
import com.carsense.features.bluetooth.presentation.screen.BluetoothDeviceScreen
import com.carsense.features.bluetooth.presentation.viewmodel.BluetoothViewModel
import com.carsense.features.dashboard.presentation.screen.DashboardScreen
import com.carsense.features.dtc.presentation.screen.DTCScreen
import com.carsense.features.sensors.presentation.screen.SensorsScreen
import com.carsense.features.welcome.presentation.screen.WelcomeScreen

@Composable
fun AppNavigation(
        navController: NavHostController,
        bluetoothViewModel: BluetoothViewModel = hiltViewModel()
) {
    val bluetoothState by bluetoothViewModel.state.collectAsState()

    // Handle state-based navigations outside of NavHost
    if (bluetoothState.isConnected &&
                    navController.currentDestination?.route != NavRoutes.DASHBOARD &&
                    navController.currentDestination?.route != NavRoutes.DTC &&
                    navController.currentDestination?.route != NavRoutes.SENSORS
    ) {
        navController.navigateAndClearBackStack(NavRoutes.DASHBOARD)
    }

    NavHost(navController = navController, startDestination = NavRoutes.WELCOME) {
        // Welcome Screen
        animatedComposable(NavRoutes.WELCOME) {
            WelcomeScreen(
                    onConnectClick = {
                        // Use single top to avoid duplicating screens in backstack
                        navController.navigateSingleTop(NavRoutes.DEVICE_LIST)
                    },
                    onSettingsClick = {
                        // Will implement settings navigation later
                    },
                    onDarkModeToggle = {
                        // Will implement theme toggle later
                    }
            )
        }

        // Device List Screen
        animatedComposable(NavRoutes.DEVICE_LIST) { backStackEntry ->
            // Create a unique key for this navigation to ensure proper state preservation
            val deviceListKey = remember(backStackEntry) { backStackEntry.id }

            BluetoothDeviceScreen(
                    state = bluetoothState,
                    onStartScan = { bluetoothViewModel.processIntent(BluetoothIntent.StartScan) },
                    onStopScan = { bluetoothViewModel.processIntent(BluetoothIntent.StopScan) },
                    onDeviceClick = { device ->
                        bluetoothViewModel.processIntent(BluetoothIntent.ConnectToDevice(device))
                    },
                    onBackPressed = {
                        // Clean navigation back to previous screen
                        navController.navigateUp()
                    }
            )
        }

        // Dashboard Screen
        animatedComposable(NavRoutes.DASHBOARD) {
            DashboardScreen(
                    state = bluetoothState,
                    onDisconnect = {
                        bluetoothViewModel.processIntent(BluetoothIntent.DisconnectFromDevice)
                        // When disconnected, go back to welcome screen
                        navController.navigateAndClearBackStack(NavRoutes.WELCOME)
                    },
                    onSendCommand = { message ->
                        bluetoothViewModel.processIntent(BluetoothIntent.SendCommand(message))
                    },
                    navigateToDTC = { navController.navigate(NavRoutes.DTC) },
                    navigateToSensors = { navController.navigate(NavRoutes.SENSORS) }
            )
        }

        // DTC Screen
        animatedComposable(NavRoutes.DTC) {
            DTCScreen(
                    onBackPressed = { navController.navigateUp() },
                    onErrorClick = { code ->
                        // Will implement DTC detail screen later
                        // navController.navigate("${NavRoutes.DTC_DETAIL}/$code")
                    }
            )
        }

        // Sensors Screen
        animatedComposable(NavRoutes.SENSORS) {
            SensorsScreen(onBackPressed = { navController.navigateUp() })
        }

        // Settings Screen (to be implemented)
        animatedComposable(NavRoutes.SETTINGS) {
            // SettingsScreen will be implemented later
        }
    }
}
