package com.carsense.core.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.carsense.core.room.dao.LocationPointDao
import com.carsense.features.vehicles.data.db.VehicleDao
import com.carsense.core.room.entity.LocationPointEntity
import com.carsense.features.vehicles.data.db.VehicleEntity

@Database(
    entities = [VehicleEntity::class, LocationPointEntity::class],
    version = 1,
    exportSchema = false // Set to true if you want to export schema to a folder
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun vehicleDao(): VehicleDao
    abstract fun locationPointDao(): LocationPointDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE
                ?: synchronized(this) {
                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "carsense_database"
                        )
                            // .addMigrations(MIGRATION_1_2) // Example: Add migrations
                            // if needed later
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
