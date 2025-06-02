package com.carsense.core.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.carsense.core.auth.AuthViewModel
import com.carsense.features.bluetooth.presentation.intent.BluetoothIntent
import com.carsense.features.bluetooth.presentation.screen.BluetoothDeviceScreen
import com.carsense.features.bluetooth.presentation.viewmodel.BluetoothViewModel
import com.carsense.features.dashboard.presentation.screen.DashboardScreen
import com.carsense.features.dtc.presentation.screen.DTCScreen
import com.carsense.features.location.presentation.screen.LocationScreen
import com.carsense.features.sensors.presentation.screen.SensorsScreen
import com.carsense.features.welcome.presentation.screen.AddVehicleScreen
import com.carsense.features.welcome.presentation.screen.WelcomeScreen
import com.carsense.features.welcome.presentation.screen.YourVehiclesScreen
import timber.log.Timber

@Composable
fun AppNavigation(
    navController: NavHostController,
    bluetoothViewModel: BluetoothViewModel = hiltViewModel(),
    onLoginClick: () -> Unit,
    authViewModel: AuthViewModel
) {
    val bluetoothState by bluetoothViewModel.state.collectAsState()
    val authState by authViewModel.state.collectAsState()

    Timber.d("AppNavigation: authState.isLoggedIn = ${authState.isLoggedIn}")
    Timber.d("AppNavigation: Passing to WelcomeScreen - isLoggedIn = ${authState.isLoggedIn}")

    // We are removing the automatic navigation to dashboard when connected
    // to allow users to stay on the welcome screen if desired

    NavHost(navController = navController, startDestination = NavRoutes.WELCOME) {
        // Welcome Screen
        animatedComposable(NavRoutes.WELCOME) {
            key(authState.isLoggedIn) {
                WelcomeScreen(
                    onConnectClick = {
                    // Use single top to avoid duplicating screens in backstack
                    navController.navigateSingleTop(NavRoutes.DEVICE_LIST)
                },
                    onDisconnectClick = {
                        bluetoothViewModel.processIntent(BluetoothIntent.DisconnectFromDevice)
                    },
                    onDashboardClick = {
                        navController.navigateSingleTop(NavRoutes.DASHBOARD)
                    },
                    onViewAllVehicles = {
                        navController.navigateSingleTop(NavRoutes.YOUR_VEHICLES)
                    },
                    onSettingsClick = {
                        // Will implement settings navigation later
                    },
                    onDarkModeToggle = {
                        // Will implement theme toggle later
                    },
                    onLoginClick = onLoginClick,
                    isLoggedIn = authState.isLoggedIn,
                    userName = authState.userName,
                    isConnected = bluetoothState.isConnected,
                    deviceName = bluetoothState.connectedDeviceAddress?.let {
                        bluetoothState.pairedDevices.firstOrNull { it.address == bluetoothState.connectedDeviceAddress }?.name
                            ?: bluetoothState.scannedDevices.firstOrNull { it.address == bluetoothState.connectedDeviceAddress }?.name
                            ?: bluetoothState.connectedDeviceAddress
                    })
            }
        }

        // Your Vehicles Screen
        animatedComposable(NavRoutes.YOUR_VEHICLES) {
            YourVehiclesScreen(
                onBackPressed = { navController.navigateUp() },
                onAddVehicle = { navController.navigateSingleTop(NavRoutes.ADD_VEHICLE) })
        }

        // Add Vehicle Screen
        animatedComposable(NavRoutes.ADD_VEHICLE) {
            AddVehicleScreen(onBackPressed = { navController.navigateUp() }, onVehicleAdded = {
                // Navigate back to Your Vehicles screen after adding a vehicle
                navController.navigateUp()
            })
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
                    // Navigate back to welcome screen after connecting
                    navController.navigateUp()
                },
                onBackPressed = {
                    // Clean navigation back to previous screen
                    navController.navigateUp()
                })
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
                navigateToSensors = { navController.navigate(NavRoutes.SENSORS) },
                navigateToLocation = { navController.navigate(NavRoutes.LOCATION) })
        }

        // DTC Screen
        animatedComposable(NavRoutes.DTC) {
            DTCScreen(onBackPressed = { navController.navigateUp() }, onErrorClick = { code ->
                // Will implement DTC detail screen later
                // navController.navigate("${NavRoutes.DTC_DETAIL}/$code")
            })
        }

        // Sensors Screen
        animatedComposable(NavRoutes.SENSORS) {
            SensorsScreen(onBackPressed = { navController.navigateUp() })
        }

        // Location Screen
        animatedComposable(NavRoutes.LOCATION) {
            LocationScreen(onBackPressed = { navController.navigateUp() })
        }

        // Settings Screen (to be implemented)
        animatedComposable(NavRoutes.SETTINGS) {
            // SettingsScreen will be implemented later
        }
    }
}
