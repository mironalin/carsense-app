package com.carsense.core.di

import android.content.Context
import com.carsense.core.auth.TokenStorageService
import com.carsense.core.room.AppDatabase
import com.carsense.core.room.dao.LocationPointDao
import com.carsense.core.room.dao.VehicleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    @Singleton // DAOs should also be singletons, tied to the single AppDatabase instance
    fun provideVehicleDao(appDatabase: AppDatabase): VehicleDao {
        return appDatabase.vehicleDao()
    }

    @Provides
    @Singleton
    fun provideLocationPointDao(appDatabase: AppDatabase): LocationPointDao {
        return appDatabase.locationPointDao()
    }

    @Provides
    @Singleton
    fun provideTokenStorageService(@ApplicationContext context: Context): TokenStorageService {
        return TokenStorageService(context)
    }
}
