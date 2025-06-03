package com.carsense.features.vehicles.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "vehicles", indices = [Index(value = ["server_id"], unique = true), Index(
        value = ["uuid"], unique = true
    ), Index(
        value = ["vehicle_identification_number"], unique = true
    ) // VIN should be unique
    ]
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "local_id") val localId: Long = 0,
    @ColumnInfo(name = "server_id") val serverId: Int? = null, // From Neon DB, nullable until synced
    @ColumnInfo(name = "uuid") val uuid: String = UUID.randomUUID()
        .toString(), // Client-generated UUID

    // Corresponds to userId in your Neon schema.
    // Type String matches Neon's text. Nullable for now.
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "vehicle_identification_number") val vehicleIdentificationNumber: String? = null,
    @ColumnInfo(name = "make") val make: String? = null,
    @ColumnInfo(name = "model") val model: String? = null,
    @ColumnInfo(name = "year") val year: String? = null, // Consider Int if validation is strict
    @ColumnInfo(name = "engine_displacement") val engineDisplacement: String? = null,
    @ColumnInfo(name = "engine_type") val engineType: String? = null,
    @ColumnInfo(name = "fuel_type") val fuelType: String? = null,
    @ColumnInfo(name = "transmission_type") val transmissionType: String? = null,
    @ColumnInfo(name = "drivetrain") val drivetrain: String? = null,
    @ColumnInfo(name = "license_plate") val licensePlate: String? = null,
    @ColumnInfo(name = "created_at_ms") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at_ms") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_synced", defaultValue = "0") val isSynced: Boolean = false
)
