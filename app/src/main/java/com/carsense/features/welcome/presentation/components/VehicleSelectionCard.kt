package com.carsense.features.welcome.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carsense.core.model.Vehicle
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Cog
import com.composables.icons.lucide.Droplets
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Settings2
import com.composables.icons.lucide.Share2
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults

@Composable
fun VehicleSelectionCard(
    modifier: Modifier = Modifier,
    vehicle: Vehicle,
    onSelect: (String) -> Unit,
    showDetailedInfo: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)
    val isSelected = vehicle.isSelected

    // Custom blend of surface colors for a more refined look
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    val cardBackgroundColor = if (isSelected) {
        // Similar blending as in SelectedVehicleCard but with higher emphasis
        androidx.compose.ui.graphics.Color(
            red = surfaceVariantColor.red * 0.6f + primaryContainerColor.red * 0.4f,
            green = surfaceVariantColor.green * 0.6f + primaryContainerColor.green * 0.4f,
            blue = surfaceVariantColor.blue * 0.6f + primaryContainerColor.blue * 0.4f,
            alpha = 1.0f
        )
    } else {
        surfaceColor
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onSelect(vehicle.uuid) },
        shape = shape,
        color = cardBackgroundColor,
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        shadowElevation = if (isSelected) 2.dp else 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(
                alpha = 0.3f
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                // Vehicle icon with background - width matches the horizontal gap (16.dp)
                Box(
                    modifier = Modifier
                        .size(
                            76.dp, 76.dp
                        )  // Width matches spacing, height matches 3 text lines
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ), contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Lucide.Car,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)  // Smaller icon
                    )
                }

                // Use consistent spacing (16.dp)
                Spacer(modifier = Modifier.width(16.dp))

                // Vehicle basic details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)  // Consistent 8.dp spacing between all items
                ) {
                    // Make and Model in main title
                    Text(
                        text = "${vehicle.make} ${vehicle.model}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Year and License Plate on same row with separator (no extra spacer needed)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${vehicle.year}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )

                        if (vehicle.licensePlate.isNotBlank()) {
                            Text(
                                text = " • ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )

                            Text(
                                text = vehicle.licensePlate,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    // Add VIN (no extra spacer needed)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "VIN: ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Medium
                        )

                        // Scrollable VIN
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = vehicle.vin,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Selection indicator
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ), contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Lucide.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Additional details if showDetailedInfo is true
            if (showDetailedInfo) {
                Spacer(modifier = Modifier.height(12.dp))

                // Content area with a subtle background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected)
                            // Increase alpha to make background more visible when selected
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    // Two Column Layout with padding between
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left column
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            DetailRow(
                                icon = Lucide.Settings2,
                                label = "Engine",
                                value = vehicle.engineType,
                                isHighlighted = isSelected
                            )

                            DetailRow(
                                icon = Lucide.Share2,
                                label = "Drivetrain",
                                value = vehicle.drivetrain,
                                isHighlighted = isSelected
                            )
                        }

                        // Right column
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            DetailRow(
                                icon = Lucide.Cog,
                                label = "Transmission",
                                value = formatTransmission(vehicle.transmissionType),
                                isHighlighted = isSelected
                            )
                            DetailRow(
                                icon = Lucide.Droplets,
                                label = "Fuel",
                                value = vehicle.fuelType,
                                isHighlighted = isSelected
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    isHighlighted: Boolean = false,
) {
    if (value.isNotBlank() && value != "Unknown") {
        Row(
            modifier = modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = "$label:",
                style = MaterialTheme.typography.bodySmall,
                color = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Formats transmission type into a shorter abbreviation.
 * Returns "AT" for automatic, "MT" for manual, or the original value if not recognized.
 */
private fun formatTransmission(transmission: String): String {
    return when {
        transmission.contains("automatic", ignoreCase = true) -> "AT"
        transmission.contains("auto", ignoreCase = true) -> "AT"
        transmission.contains("manual", ignoreCase = true) -> "MT"
        transmission.contains("stick", ignoreCase = true) -> "MT"
        else -> transmission
    }
}

@Composable
fun AddVehicleButton(
    onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 2.dp, pressedElevation = 4.dp
        )
    ) {
        Text(
            text = "Add New Vehicle", style = MaterialTheme.typography.titleMedium
        )
    }
} 