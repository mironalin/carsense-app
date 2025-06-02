package com.carsense.features.welcome.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.carsense.core.model.Vehicle
import com.carsense.ui.theme.CarSenseTheme
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide

@Composable
fun SelectedVehicleCard(
    vehicle: Vehicle?, onViewAllVehicles: () -> Unit, modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

    // Create a less green color by blending surface with primaryContainer
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    // Manual color blending - mix the surface color with a small amount of the primary container
    val blendedColor = androidx.compose.ui.graphics.Color(
        red = surfaceVariantColor.red * 0.7f + primaryContainerColor.red * 0.3f,
        green = surfaceVariantColor.green * 0.7f + primaryContainerColor.green * 0.3f,
        blue = surfaceVariantColor.blue * 0.7f + primaryContainerColor.blue * 0.3f,
        alpha = 1.0f
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = blendedColor
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 16.dp)
        ) {
            // Header with badge and controls
            Row(
                modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                    shadowElevation = 2.dp,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(
                        text = "SELECTED VEHICLE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Expand/collapse indicator
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { isExpanded = !isExpanded }) {
                    Box(
                        contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Lucide.ChevronDown,
                            contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(rotationAngle)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Button to change vehicle
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onViewAllVehicles() }) {
                    Box(
                        contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Lucide.ChevronRight,
                            contentDescription = "View all vehicles",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (vehicle != null) {
                // Main content with vehicle details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Car icon in a circle
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                        shadowElevation = 4.dp,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Lucide.Car,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Vehicle information with larger text
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Display make and model as main title
                        Text(
                            text = "${vehicle.make} ${vehicle.model}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Year and License Plate on same row with separator
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
                    }
                }

                // Expandable details section
                AnimatedVisibility(
                    visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp, end = 16.dp, bottom = 16.dp
                        )
                    ) {
                        // Divider for separating sections
                        HorizontalDivider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )

                        // VIN number
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            SpecItem(
                                label = "VIN",
                                value = vehicle.vin,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Specifications table
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SpecItem(
                                    label = "Engine",
                                    value = vehicle.engineType,
                                    modifier = Modifier.weight(1f)
                                )
                                SpecItem(
                                    label = "Fuel",
                                    value = vehicle.fuelType,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SpecItem(
                                    label = "Transmission",
                                    value = vehicle.transmissionType,
                                    modifier = Modifier.weight(1f)
                                )
                                SpecItem(
                                    label = "Drivetrain",
                                    value = vehicle.drivetrain,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            } else {
                // No vehicle selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    imageVector = Lucide.Car,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No vehicle selected yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Tap to select a vehicle",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecItem(
    label: String, value: String, modifier: Modifier = Modifier
) {
    val displayValue = if (value.isBlank()) "Not specified" else value

    Column(
        modifier = modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = displayValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = if (displayValue == "Not specified") FontWeight.Normal else FontWeight.Medium
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SelectedVehicleCardPreview() {
    CarSenseTheme {
        SelectedVehicleCard(
            vehicle = Vehicle(
                uuid = "123",
                make = "Toyota",
                model = "Corolla",
                year = 2022,
                engineType = "1.8L I4",
                fuelType = "Gasoline",
                transmissionType = "Automatic",
                drivetrain = "FWD",
                vin = "JT2BF22K1W0123456",
                licensePlate = "ABC-123",
                isSelected = true
            ), onViewAllVehicles = {})
    }
}

@Preview(showBackground = true)
@Composable
fun SelectedVehicleCardPreviewNoVehicle() {
    CarSenseTheme {
        SelectedVehicleCard(
            vehicle = null, onViewAllVehicles = {})
    }
} 