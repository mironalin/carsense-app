package com.carsense.features.bluetooth.presentation.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carsense.features.bluetooth.domain.BluetoothDeviceDomain
import com.carsense.features.bluetooth.presentation.model.BluetoothState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDeviceScreen(
    state: BluetoothState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (BluetoothDeviceDomain) -> Unit,
    onBackPressed: () -> Unit = {}
) {
    // Handle back button press
    BackHandler { onBackPressed() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select a Device") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            BluetoothDeviceList(
                scannedDevices = state.scannedDevices,
                pairedDevices = state.pairedDevices,
                onClick = onDeviceClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onStartScan) { Text("Start Scan") }
                Button(onClick = onStopScan) { Text("Stop Scan") }
            }
        }
    }
}

@Composable
fun BluetoothDeviceList(
    scannedDevices: List<BluetoothDeviceDomain>,
    pairedDevices: List<BluetoothDeviceDomain>,
    onClick: (BluetoothDeviceDomain) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        item {
            Text(
                text = "Paired Devices",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(pairedDevices) { device -> BluetoothDeviceItem(device = device, onClick = onClick) }

        if (pairedDevices.isEmpty()) {
            item {
                Text(
                    text = "No paired devices found",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanned Devices",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        items(scannedDevices) { device -> BluetoothDeviceItem(device = device, onClick = onClick) }

        if (scannedDevices.isEmpty()) {
            item {
                Text(
                    text = "No scanned devices found",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun BluetoothDeviceItem(device: BluetoothDeviceDomain, onClick: (BluetoothDeviceDomain) -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clickable { onClick(device) },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = device.name ?: "Unknown Device",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(text = device.address, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
