package com.carsense.features.vehicles.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * DataStore instance for storing vehicle preferences.
 * This is used by the VehicleRepositoryImpl to store selected vehicle and other preferences.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vehicle_preferences") 