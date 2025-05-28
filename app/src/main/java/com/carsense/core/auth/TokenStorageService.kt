package com.carsense.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenStorageService @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        private const val PREF_FILE_NAME = "carsense_auth_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_CSRF_STATE = "csrf_state"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val sharedPreferences by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                PREF_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.e(e, "Error creating EncryptedSharedPreferences")
            // Fallback to regular SharedPreferences if EncryptedSharedPreferences fails
            // This is not ideal for production but can prevent crashes during development/testing issues
            // Consider a more robust error handling or app state for production.
            context.getSharedPreferences(
                PREF_FILE_NAME + "_unencrypted_fallback",
                Context.MODE_PRIVATE
            )
        }
    }

    fun saveAuthToken(token: String?) {
        if (token == null) {
            sharedPreferences.edit().remove(KEY_AUTH_TOKEN).apply()
            Timber.d("Auth token cleared.")
        } else {
            sharedPreferences.edit().putString(KEY_AUTH_TOKEN, token).apply()
            Timber.d("Auth token saved.")
        }
    }

    fun getAuthToken(): String? {
        return sharedPreferences.getString(KEY_AUTH_TOKEN, null)
    }

    fun clearAuthToken() {
        saveAuthToken(null)
    }

    fun saveCsrfState(state: String) {
        sharedPreferences.edit().putString(KEY_CSRF_STATE, state).apply()
        Timber.d("CSRF state saved.")
    }

    fun getCsrfState(): String? {
        return sharedPreferences.getString(KEY_CSRF_STATE, null)
    }

    fun clearCsrfState() {
        sharedPreferences.edit().remove(KEY_CSRF_STATE).apply()
        Timber.d("CSRF state cleared.")
    }
} 