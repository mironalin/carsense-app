package com.carsense.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.carsense.features.bluetooth.presentation.intent.BluetoothIntent
import com.carsense.features.bluetooth.presentation.screen.BluetoothDeviceScreen
import com.carsense.features.bluetooth.presentation.viewmodel.BluetoothViewModel
import com.carsense.features.dashboard.presentation.screen.DashboardScreen
import com.carsense.features.welcome.presentation.screen.WelcomeScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    bluetoothViewModel: BluetoothViewModel = hiltViewModel()
) {
    val bluetoothState by bluetoothViewModel.state.collectAsState()

    // Handle state-based navigations outside of NavHost
    if (bluetoothState.isConnected && navController.currentDestination?.route != NavRoutes.DASHBOARD
    ) {
        navController.navigate(NavRoutes.DASHBOARD) {
            // Clear backstack when connected
            popUpTo(NavRoutes.WELCOME) { inclusive = true }
        }
    }

    NavHost(navController = navController, startDestination = NavRoutes.WELCOME) {
        composable(NavRoutes.WELCOME) {
            WelcomeScreen(
                onConnectClick = { navController.navigate(NavRoutes.DEVICE_LIST) },
                onSettingsClick = {
                    // Will implement settings navigation later
                    // navController.navigate(NavRoutes.SETTINGS)
                },
                onDarkModeToggle = {
                    // Will implement theme toggle later
                }
            )
        }

        composable(NavRoutes.DEVICE_LIST) { backStackEntry ->
            // Create a unique key for this navigation to ensure proper state preservation
            val deviceListKey = remember(backStackEntry) { backStackEntry.id }

            BluetoothDeviceScreen(
                state = bluetoothState,
                onStartScan = { bluetoothViewModel.processIntent(BluetoothIntent.StartScan) },
                onStopScan = { bluetoothViewModel.processIntent(BluetoothIntent.StopScan) },
                onDeviceClick = { device ->
                    bluetoothViewModel.processIntent(BluetoothIntent.ConnectToDevice(device))
                },
//                    onStartServer = {
//                        bluetoothViewModel.processIntent(BluetoothIntent.WaitForConnections)
//                    },
                onBackPressed = {
                    // Allow navigating back to welcome screen
                    navController.navigateUp()
                }
            )
        }

        composable(NavRoutes.DASHBOARD) {
            DashboardScreen(
                state = bluetoothState,
                onDisconnect = {
                    bluetoothViewModel.processIntent(BluetoothIntent.DisconnectFromDevice)
                    // When disconnected, go back to welcome screen
                    navController.navigate(NavRoutes.WELCOME) {
                        popUpTo(NavRoutes.WELCOME) { inclusive = true }
                    }
                },
                onSendCommand = { message ->
                    bluetoothViewModel.processIntent(BluetoothIntent.SendCommand(message))
                }
            )
        }

        // We'll add settings screen later
        composable(NavRoutes.SETTINGS) {
            // SettingsScreen will be implemented later
        }
    }
}
