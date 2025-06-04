package com.carsense.features.sensors.di

import com.carsense.features.sensors.data.api.SensorApiService
import com.carsense.features.sensors.data.repository.SensorRepositoryImpl
import com.carsense.features.sensors.domain.repository.SensorRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorsModule {

    @Binds
    @Singleton
    abstract fun bindSensorRepository(sensorRepositoryImpl: SensorRepositoryImpl): SensorRepository

    companion object {
        @Provides
        @Singleton
        fun provideSensorApiService(retrofit: Retrofit): SensorApiService {
            return retrofit.create(SensorApiService::class.java)
        }
    }
}
