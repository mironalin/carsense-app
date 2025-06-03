package com.carsense.features.location.data.mapper

import com.carsense.core.room.entity.LocationPointEntity
import com.carsense.features.location.domain.model.LocationPoint

/**
 * Helper class to map between domain model and entity model
 */
object LocationMapper {

    /**
     * Maps a domain model LocationPoint to a database entity
     */
    fun toEntity(domainModel: LocationPoint): LocationPointEntity {
        return LocationPointEntity(
            uuid = domainModel.uuid,
            vehicleLocalId = domainModel.vehicleLocalId,
            latitude = domainModel.latitude,
            longitude = domainModel.longitude,
            altitude = domainModel.altitude,
            speed = domainModel.speed,
            accuracy = domainModel.accuracy,
            timestamp = domainModel.timestamp,
            isSynced = domainModel.isSynced
        )
    }

    /**
     * Maps a database entity to a domain model LocationPoint
     */
    fun toDomainModel(entity: LocationPointEntity): LocationPoint {
        return LocationPoint(
            uuid = entity.uuid,
            vehicleLocalId = entity.vehicleLocalId ?: -1, // Handle nullable vehicle ID
            latitude = entity.latitude,
            longitude = entity.longitude,
            altitude = entity.altitude,
            speed = entity.speed,
            accuracy = entity.accuracy,
            timestamp = entity.timestamp,
            isSynced = entity.isSynced
        )
    }

    /**
     * Maps a list of domain models to a list of entities
     */
    fun toEntityList(domainModels: List<LocationPoint>): List<LocationPointEntity> {
        return domainModels.map { toEntity(it) }
    }

    /**
     * Maps a list of entities to a list of domain models
     */
    fun toDomainModelList(entities: List<LocationPointEntity>): List<LocationPoint> {
        return entities.map { toDomainModel(it) }
    }
} 