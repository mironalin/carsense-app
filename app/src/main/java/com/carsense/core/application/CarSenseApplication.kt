package com.carsense.core.application

// import com.carsense.BuildConfig // Remove explicit import for now
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CarSenseApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Use fully qualified name, ensure your applicationId is 'com.carsense'
        if (com.carsense.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Timber DebugTree planted.")
        } else {
            // TODO: Plant a release tree for production (e.g., CrashlyticsTree)
            // Timber.plant(CrashlyticsTree())
        }
    }
}
