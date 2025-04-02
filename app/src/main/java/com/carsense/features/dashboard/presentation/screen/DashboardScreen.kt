package com.carsense.features.dashboard.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carsense.features.bluetooth.presentation.model.BluetoothState
import com.carsense.features.obd2.presentation.model.MessageModel

/**
 * Screen for OBD2 diagnostics that allows sending commands and viewing responses. Provides a set of
 * common OBD2 commands as buttons and displays the command history.
 */
@Composable
fun DashboardScreen(
    state: BluetoothState,
    onDisconnect: () -> Unit,
    onSendCommand: (String) -> Unit
) {
    var command by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "OBD2 Diagnostics",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Message display area
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 8.dp)
        ) {
            items(state.messages) { message ->
                MessageItem(message = message)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // Command input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                label = { Text("OBD2 Command") },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    if (command.isNotBlank()) {
                        onSendCommand(command)
                        command = ""
                    }
                }
            ) { Text("Send") }
        }

        // Quick commands
        QuickCommandsSection(
            onCommandSelected = { selectedCommand -> onSendCommand(selectedCommand) }
        )

        // Disconnect button
        Button(
            onClick = onDisconnect,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
        ) { Text("Disconnect") }
    }
}

@Composable
fun MessageItem(message: MessageModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = message.message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun QuickCommandsSection(onCommandSelected: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Quick Commands",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            CommandChip(text = "RPM", command = "010C", onCommandSelected = onCommandSelected)
            CommandChip(text = "Speed", command = "010D", onCommandSelected = onCommandSelected)
            CommandChip(text = "Temp", command = "0105", onCommandSelected = onCommandSelected)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CommandChip(text = "VIN", command = "0902", onCommandSelected = onCommandSelected)
            CommandChip(text = "Fuel", command = "012F", onCommandSelected = onCommandSelected)
            CommandChip(text = "DTC", command = "03", onCommandSelected = onCommandSelected)
        }
    }
}

@Composable
fun CommandChip(text: String, command: String, onCommandSelected: (String) -> Unit) {
    SuggestionChip(onClick = { onCommandSelected(command) }, label = { Text(text) })
}
