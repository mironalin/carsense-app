package com.carsense.features.sensors.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sensorDataStore: DataStore<Preferences> by preferencesDataStore(name = "sensor_preferences")

@Singleton
class SensorPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object PreferencesKeys {
        val SELECTED_SENSORS = stringSetPreferencesKey("selected_sensors")
        val SENSOR_SLOTS = stringPreferencesKey("sensor_slots") // Comma-separated ordered list
    }

    /**
     * Flow of currently selected sensor IDs
     */
    val selectedSensors: Flow<Set<String>> = context.sensorDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SELECTED_SENSORS] ?: getDefaultSelectedSensors()
        }

    /**
     * Save the selected sensor IDs
     */
    suspend fun saveSelectedSensors(sensorIds: Set<String>) {
        context.sensorDataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_SENSORS] = sensorIds
        }
    }

    /**
     * Get sensor slots (ordered list of sensor IDs, max 8)
     */
    suspend fun getSensorSlots(): List<String> {
        val preferences = context.sensorDataStore.data.first()
        val slotsString = preferences[PreferencesKeys.SENSOR_SLOTS]
        return if (slotsString.isNullOrEmpty()) {
            getDefaultSensorSlots()
        } else {
            slotsString.split(",").filter { it.isNotEmpty() }.take(8)
        }
    }

    /**
     * Save sensor slots (ordered list of sensor IDs)
     */
    suspend fun saveSensorSlots(sensorIds: List<String>) {
        context.sensorDataStore.edit { preferences ->
            preferences[PreferencesKeys.SENSOR_SLOTS] = sensorIds.take(8).joinToString(",")
        }
    }

    /**
     * Get the default selected sensors (RPM, Speed, Coolant, Throttle)
     */
    private fun getDefaultSelectedSensors(): Set<String> {
        return setOf("rpm", "speed", "coolant", "throttle")
    }

    /**
     * Get the default sensor slots (ordered: RPM, Speed, Coolant, Throttle)
     */
    private fun getDefaultSensorSlots(): List<String> {
        return listOf("rpm", "speed", "coolant", "throttle")
    }

    /**
     * Clear all sensor preferences
     */
    suspend fun clearSelectedSensors() {
        context.sensorDataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.SELECTED_SENSORS)
            preferences.remove(PreferencesKeys.SENSOR_SLOTS)
        }
    }
} 