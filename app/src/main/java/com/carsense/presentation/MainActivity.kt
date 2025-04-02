package com.carsense.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.carsense.presentation.components.DeviceScreen
import com.carsense.presentation.components.OBD2Screen
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

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            state.isConnecting -> {
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

            state.isConnected -> {
                OBD2Screen(
                    state = state,
                    onDisconnect = viewModel::disconnectFromDevice,
                    onSendCommand = viewModel::sendMessage
                )
            }

            else -> {
                Column {
                    // Add app title and description at the top
                    Text(
                        text = "CarSense OBD2 Diagnostics",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        text = "Connect to an ELM327 OBD2 adapter to read vehicle data",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    // Show device list for connection
                    DeviceScreen(
                        state = state,
                        onStartScan = viewModel::startScan,
                        onStopScan = viewModel::stopScan,
                        onDeviceClick = viewModel::connectToDevice,
                        onStartServer = viewModel::waitForIncomingConnections
                    )
                }
            }
        }

        // Show error messages if any
        state.errorMessage?.let { errorMessage ->
            AlertDialog(
                onDismissRequest = { /* No-op */ },
                title = { Text("Connection Error") },
                text = { Text(errorMessage) },
                confirmButton = {
                    Button(onClick = viewModel::disconnectFromDevice) { Text("OK") }
                }
            )
        }
    }
}
