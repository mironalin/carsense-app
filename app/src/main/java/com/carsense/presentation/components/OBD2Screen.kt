package com.carsense.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import com.carsense.presentation.BluetoothUiState

/**
 * Screen for OBD2 diagnostics that allows sending commands and viewing responses. Provides a set of
 * common OBD2 commands as buttons and displays the command history.
 */
@Composable
fun OBD2Screen(state: BluetoothUiState, onDisconnect: () -> Unit, onSendCommand: (String) -> Unit) {
    var command = remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with title and disconnect button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "OBD2 Diagnostics",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge
            )
            IconButton(onClick = onDisconnect) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Disconnect"
                )
            }
        }

        // Common command buttons
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = "Common Commands",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )

            // Engine data commands
            CommandButtonGroup(
                title = "Engine Data",
                commands =
                    listOf(
                        CommandButton("RPM", "010C"),
                        CommandButton("Speed", "010D"),
                        CommandButton("Coolant", "0105")
                    ),
                onSendCommand = onSendCommand
            )

            // Sensor data commands
            CommandButtonGroup(
                title = "Sensors",
                commands =
                    listOf(
                        CommandButton("MAP", "010B"),
                        CommandButton("Throttle", "0111"),
                        CommandButton("Air Temp", "010F")
                    ),
                onSendCommand = onSendCommand
            )

            // Diagnostic commands
            CommandButtonGroup(
                title = "Diagnostics",
                commands =
                    listOf(
                        CommandButton("DTC Count", "0101"),
                        CommandButton("Clear DTCs", "04"),
                        CommandButton("O2 Sensors", "0113")
                    ),
                onSendCommand = onSendCommand
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Command history
        Text(
            text = "Response History",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages) { message ->
                if (message.isFromLocalUser) {
                    // This is a command sent by the user
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    MaterialTheme.colorScheme
                                        .primaryContainer
                            )
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Command:",
                                style =
                                    MaterialTheme.typography
                                        .labelMedium,
                                modifier =
                                    Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = message.content,
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium
                            )
                        }
                    }
                } else {
                    // This is a response
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.cardColors(
                                containerColor =
                                    if (message.senderName ==
                                        "Error"
                                    )
                                        MaterialTheme
                                            .colorScheme
                                            .errorContainer
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .secondaryContainer
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = message.content,
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium,
                                color =
                                    if (message.senderName ==
                                        "Error"
                                    )
                                        MaterialTheme
                                            .colorScheme
                                            .onErrorContainer
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Command input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = command.value,
                onValueChange = { command.value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(text = "Enter OBD2 command (e.g. 010C)") },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (command.value.isNotBlank()) {
                        onSendCommand(command.value)
                        command.value = ""
                        keyboardController?.hide()
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send command"
                )
            }
        }
    }
}

/** Data class representing a command button */
data class CommandButton(val label: String, val command: String)

/** A group of related OBD2 command buttons with a title */
@Composable
fun CommandButtonGroup(
    title: String,
    commands: List<CommandButton>,
    onSendCommand: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            commands.forEach { cmdButton ->
                OutlinedButton(
                    onClick = { onSendCommand(cmdButton.command) },
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) { Text(cmdButton.label) }
            }
        }
    }
}
