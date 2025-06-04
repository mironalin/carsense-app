package com.carsense.features.diagnostics.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.diagnosticPrefs by preferencesDataStore(name = "diagnostic_session_prefs")

/**
 * Manages the currently active diagnostic session UUID.
 * This is used to associate DTCs and other diagnostic data with the correct diagnostic record.
 */
@Singleton
class DiagnosticSessionManager @Inject constructor(
    private val context: Context
) {
    private val TAG = "DiagnosticSessionManager"

    companion object {
        private val CURRENT_DIAGNOSTIC_UUID = stringPreferencesKey("current_diagnostic_uuid")
    }

    /**
     * Sets the current diagnostic UUID for this session
     */
    suspend fun setCurrentDiagnosticUUID(uuid: String) {
        Log.d(TAG, "Setting current diagnostic UUID: $uuid")
        context.diagnosticPrefs.edit { prefs ->
            prefs[CURRENT_DIAGNOSTIC_UUID] = uuid
        }
    }

    /**
     * Gets the current diagnostic UUID as a Flow
     */
    fun getCurrentDiagnosticUUIDAsFlow(): Flow<String?> {
        return context.diagnosticPrefs.data.map { prefs ->
            prefs[CURRENT_DIAGNOSTIC_UUID]
        }
    }

    /**
     * Gets the current diagnostic UUID as a suspending function
     */
    suspend fun getCurrentDiagnosticUUID(): String? {
        return getCurrentDiagnosticUUIDAsFlow().firstOrNull()
    }

    /**
     * Clears the current diagnostic UUID when the session ends
     */
    suspend fun clearCurrentDiagnosticUUID() {
        Log.d(TAG, "Clearing current diagnostic UUID")
        context.diagnosticPrefs.edit { prefs ->
            prefs.remove(CURRENT_DIAGNOSTIC_UUID)
        }
    }
} 