package com.carsense.features.obd2.di

import com.carsense.features.obd2.data.repository.DTCRepositoryImpl
import com.carsense.features.obd2.domain.repository.DTCRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OBD2Module {

    @Binds
    @Singleton
    abstract fun bindDTCRepository(dtcRepositoryImpl: DTCRepositoryImpl): DTCRepository
}
