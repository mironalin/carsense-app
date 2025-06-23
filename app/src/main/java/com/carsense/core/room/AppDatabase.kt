package com.carsense.core.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.carsense.core.room.dao.LocationPointDao
import com.carsense.features.vehicles.data.db.VehicleDao
import com.carsense.core.room.entity.LocationPointEntity
import com.carsense.features.vehicles.data.db.VehicleEntity

@Database(
    entities = [VehicleEntity::class, LocationPointEntity::class],
    version = 3,
    exportSchema = false // Set to true if you want to export schema to a folder
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    abstract fun locationPointDao(): LocationPointDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2
        // Changes:
        // 1. Removed server_id column from vehicles table
        // 2. Changed foreign key in location_points from vehicle_local_id to vehicle_uuid
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create new vehicles table without server_id
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vehicles_new (
                        local_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        uuid TEXT NOT NULL,
                        user_id TEXT,
                        vehicle_identification_number TEXT,
                        make TEXT,
                        model TEXT,
                        year TEXT,
                        engine_displacement TEXT,
                        engine_type TEXT,
                        fuel_type TEXT,
                        transmission_type TEXT,
                        drivetrain TEXT,
                        license_plate TEXT,
                        created_at_ms INTEGER NOT NULL,
                        updated_at_ms INTEGER NOT NULL,
                        is_synced INTEGER NOT NULL DEFAULT 0
                    )
                    """
                )

                // Copy data from old table to new table, excluding server_id
                database.execSQL(
                    """
                    INSERT INTO vehicles_new (
                        local_id, uuid, user_id, vehicle_identification_number,
                        make, model, year, engine_displacement, engine_type,
                        fuel_type, transmission_type, drivetrain, license_plate,
                        created_at_ms, updated_at_ms, is_synced
                    )
                    SELECT
                        local_id, uuid, user_id, vehicle_identification_number,
                        make, model, year, engine_displacement, engine_type,
                        fuel_type, transmission_type, drivetrain, license_plate,
                        created_at_ms, updated_at_ms, is_synced
                    FROM vehicles
                    """
                )

                // Drop the old table
                database.execSQL("DROP TABLE vehicles")

                // Rename the new table to the original name
                database.execSQL("ALTER TABLE vehicles_new RENAME TO vehicles")

                // Create indices for the new vehicles table
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vehicles_uuid ON vehicles(uuid)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_vehicles_vehicle_identification_number ON vehicles(vehicle_identification_number)")

                // Create new location_points table with vehicle_uuid instead of vehicle_local_id
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS location_points_new (
                        local_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        uuid TEXT NOT NULL,
                        vehicle_uuid TEXT,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        altitude REAL,
                        speed_mps REAL,
                        accuracy_meters REAL,
                        timestamp INTEGER NOT NULL,
                        is_synced INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (vehicle_uuid) REFERENCES vehicles(uuid) ON DELETE CASCADE
                    )
                    """
                )

                // Copy data from old table to new table, joining with vehicles to get uuid
                database.execSQL(
                    """
                    INSERT INTO location_points_new (
                        local_id, uuid, vehicle_uuid, latitude, longitude,
                        altitude, speed_mps, accuracy_meters, timestamp, is_synced
                    )
                    SELECT
                        lp.local_id, lp.uuid, v.uuid, lp.latitude, lp.longitude,
                        lp.altitude, lp.speed_mps, lp.accuracy_meters, lp.timestamp, lp.is_synced
                    FROM location_points lp
                    LEFT JOIN vehicles v ON lp.vehicle_local_id = v.local_id
                    """
                )

                // Drop the old table
                database.execSQL("DROP TABLE location_points")

                // Rename the new table to the original name
                database.execSQL("ALTER TABLE location_points_new RENAME TO location_points")

                // Create indices for the new location_points table
                database.execSQL("CREATE INDEX IF NOT EXISTS index_location_points_vehicle_uuid ON location_points(vehicle_uuid)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_location_points_uuid ON location_points(uuid)")
            }
        }

        // Migration from version 2 to 3
        // Changes:
        // 1. Added diagnostic_uuid column to location_points table
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add diagnostic_uuid column to location_points table
                database.execSQL("ALTER TABLE location_points ADD COLUMN diagnostic_uuid TEXT")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "carsense_database"
                        )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Add the migrations
                            .fallbackToDestructiveMigration(false) // If migrations are not
                            // provided, recreate the
                            // DB (not for production)
                            .build()
                    INSTANCE = instance
                    instance
                }
        }
    }
}
