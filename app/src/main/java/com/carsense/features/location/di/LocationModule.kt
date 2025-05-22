package com.carsense.features.location.di

import android.content.Context
import com.carsense.features.location.data.service.AndroidLocationService
import com.carsense.features.location.data.service.LocationService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideFusedLocationProviderClient(
        @ApplicationContext context: Context
    ): FusedLocationProviderClient {
        return LocationServices.getFusedLocationProviderClient(context)
    }
}

@Module
@InstallIn(SingletonComponent::class) // Or ActivityRetainedComponent if tied to ViewModel lifecycle
abstract class LocationServiceModule {

    @Binds
    @Singleton
    abstract fun bindLocationService(impl: AndroidLocationService): LocationService
}
