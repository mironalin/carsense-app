package com.carsense.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

object LocationPermissionHelper {

    private val FOREGROUND_LOCATION_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // This can be used by an external caller if they specifically want to request all upfront,
    // but our main flow will be staged.
    fun getAllPossibleLocationPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            FOREGROUND_LOCATION_PERMISSIONS + Manifest.permission.ACCESS_BACKGROUND_LOCATION
        } else {
            FOREGROUND_LOCATION_PERMISSIONS
        }
    }

    fun getForegroundLocationPermissions(): Array<String> {
        return FOREGROUND_LOCATION_PERMISSIONS
    }

    fun hasFineLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCoarseLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required before Android Q
        }
    }

    fun hasRequiredLocationPermissions(context: Context): Boolean {
        val hasFineOrCoarse =
            hasFineLocationPermission(context) || hasCoarseLocationPermission(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return hasFineOrCoarse && hasBackgroundLocationPermission(context)
        } else {
            return hasFineOrCoarse
        }
    }

    fun createPermissionLauncher(
        activity: ComponentActivity,
        onResult: (Map<String, Boolean>) -> Unit
    ): ActivityResultLauncher<Array<String>> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
            onResult
        )
    }

    /**
     * A utility to launch the permission request.
     * The launcher should be created and managed by the calling Activity/Fragment.
     */
    fun requestLocationPermissions(
        launcher: ActivityResultLauncher<Array<String>>
    ) {
        // Now only requests foreground permissions
        launcher.launch(FOREGROUND_LOCATION_PERMISSIONS)
    }

    /**
     * Specifically request background location permission if fine/coarse is already granted.
     * This is useful for Android 11+ where background permission is requested separately.
     */
    fun requestBackgroundLocationPermission(
        launcher: ActivityResultLauncher<String> // Note: Single permission launcher for background
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    fun createBackgroundPermissionLauncher(
        activity: ComponentActivity,
        onResult: (Boolean) -> Unit // Callback for single permission
    ): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
            onResult
        )
    }
}