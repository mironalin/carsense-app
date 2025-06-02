package com.carsense.core.di

import com.carsense.core.repository.VehicleRepository
import com.carsense.core.repository.VehicleRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVehicleRepository(vehicleRepositoryImpl: VehicleRepositoryImpl): VehicleRepository
} 