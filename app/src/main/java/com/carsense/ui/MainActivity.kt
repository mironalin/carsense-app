package com.carsense.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carsense.features.bluetooth.presentation.intent.BluetoothIntent
import com.carsense.features.bluetooth.presentation.screen.BluetoothDeviceScreen
import com.carsense.features.bluetooth.presentation.viewmodel.BluetoothViewModel
import com.carsense.features.dashboard.presentation.screen.DashboardScreen
import com.carsense.features.welcome.presentation.screen.WelcomeScreen
import com.carsense.ui.theme.CarSenseTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val bluetoothManager by lazy {
        applicationContext.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }

    private val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val enableBluetoothLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { /* Not needed */ }

        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
                val canEnableBluetooth =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        perms[Manifest.permission.BLUETOOTH_CONNECT] == true
                    } else true

                if (canEnableBluetooth && !isBluetoothEnabled) {
                    enableBluetoothLauncher.launch(
                        Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    )
                }
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            )
        }

        setContent {
            CarSenseTheme {
                val viewModel = hiltViewModel<BluetoothViewModel>()
                MainScreen(viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: BluetoothViewModel) {
    val state by viewModel.state.collectAsState()
    var showDeviceList by remember { mutableStateOf(false) }

    when {
        state.isConnecting -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Connecting to OBD2 device...")
                }
            }
        }

        state.isConnected -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
            ) {
                DashboardScreen(
                    state = state,
                    onDisconnect = {
                        viewModel.processIntent(BluetoothIntent.DisconnectFromDevice)
                    },
                    onSendCommand = { message ->
                        viewModel.processIntent(BluetoothIntent.SendCommand(message))
                    }
                )
            }
        }

        showDeviceList -> {
            Surface(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column {
                    // Show device list for connection
                    BluetoothDeviceScreen(
                        state = state,
                        onStartScan = { viewModel.processIntent(BluetoothIntent.StartScan) },
                        onStopScan = { viewModel.processIntent(BluetoothIntent.StopScan) },
                        onDeviceClick = { device ->
                            viewModel.processIntent(BluetoothIntent.ConnectToDevice(device))
                        },
                        onStartServer = {
                            viewModel.processIntent(BluetoothIntent.WaitForConnections)
                        }
                    )
                }
            }
        }

        else -> {
            Surface {
                WelcomeScreen(
                    onConnectClick = { showDeviceList = true },
                    onSettingsClick = { /* Handle settings click */ },
                    onDarkModeToggle = { /* Handle dark mode toggle */ }
                )
            }
        }
    }

    // Show error messages if any
    state.errorMessage?.let { errorMessage ->
        AlertDialog(
            onDismissRequest = { viewModel.processIntent(BluetoothIntent.DismissError) },
            title = { Text("Connection Error") },
            text = { Text(errorMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.processIntent(BluetoothIntent.DisconnectFromDevice)
                    }
                ) { Text("OK") }
            }
        )
    }
}
