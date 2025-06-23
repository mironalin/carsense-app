package com.carsense.features.location.data.mapper

import com.carsense.core.room.entity.LocationPointEntity
import com.carsense.features.location.domain.model.LocationPoint
import com.carsense.features.location.data.api.CreateLocationRequest
import com.carsense.features.location.data.api.LocationDto

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
            vehicleUUID = domainModel.vehicleUUID,
            diagnosticUUID = domainModel.diagnosticUUID,
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
            vehicleUUID = entity.vehicleUUID ?: "", // Handle nullable vehicle UUID
            diagnosticUUID = entity.diagnosticUUID,
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

    /**
     * Maps a domain model LocationPoint to an API request
     */
    fun toApiRequest(domainModel: LocationPoint): CreateLocationRequest {
        return CreateLocationRequest(
            vehicleUUID = domainModel.vehicleUUID,
            latitude = domainModel.latitude,
            longitude = domainModel.longitude,
            altitude = domainModel.altitude,
            speed = domainModel.speed?.toDouble(),
            accuracy = domainModel.accuracy?.toDouble(),
            timestamp = java.time.Instant.ofEpochMilli(domainModel.timestamp)
                .toString() // Convert to ISO 8601 string
        )
    }

    /**
     * Maps a list of domain models to a list of API requests
     */
    fun toApiRequestList(domainModels: List<LocationPoint>): List<CreateLocationRequest> {
        return domainModels.map { toApiRequest(it) }
    }

    /**
     * Maps an API response DTO to a domain model
     */
    fun fromApiResponse(dto: LocationDto): LocationPoint {
        return LocationPoint(
            uuid = dto.uuid,
            vehicleUUID = dto.vehicleUUID,
            diagnosticUUID = dto.diagnosticUUID,
            latitude = dto.latitude,
            longitude = dto.longitude,
            altitude = dto.altitude,
            speed = dto.speed?.toFloat(),
            accuracy = dto.accuracy?.toFloat(),
            timestamp = try {
                java.time.Instant.parse(dto.timestamp).toEpochMilli()
            } catch (e: Exception) {
                System.currentTimeMillis() // Fallback to current time if parsing fails
            },
            isSynced = true // Mark as synced since it comes from API
        )
    }
} 