package com.xlabsx27.naveeoffline.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceM: Double = 0.0,
    /** Time spent faster than walking pace; average speed is distance over this. */
    val movingMs: Long = 0,
    val maxSpeedKmh: Double = 0.0,
    val energyWh: Double = 0.0,
    val socStart: Int? = null,
    val socEnd: Int? = null,
    val odometerStartKm: Double? = null,
    val odometerEndKm: Double? = null,
    val scooterName: String? = null,
) {
    val durationMs: Long get() = (endedAt ?: System.currentTimeMillis()) - startedAt
    val avgSpeedKmh: Double get() = if (movingMs > 0) distanceM / (movingMs / 1000.0) * 3.6 else 0.0
    val whPerKm: Double? get() = if (distanceM > 100 && energyWh > 0) energyWh / (distanceM / 1000) else null
    val socUsed: Int? get() = if (socStart != null && socEnd != null) socStart - socEnd else null
}

@Entity(
    tableName = "track_points",
    foreignKeys = [ForeignKey(Trip::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val accuracyM: Float? = null,
    val gpsSpeedKmh: Float? = null,
    val scooterSpeedKmh: Float? = null,
    val soc: Int? = null,
    val voltage: Float? = null,
    val currentA: Float? = null,
)

object ParkReason {
    /** Saved when the scooter was locked. Authoritative until it is unlocked again. */
    const val LOCKED = "locked"
    /** Saved when the link dropped while unlocked: roughly where the rider walked out of range. */
    const val OUT_OF_RANGE = "out_of_range"
    const val MANUAL = "manual"
}

/** Where the scooter was last seen: the phone's position when the link dropped or it was locked. */
@Entity(tableName = "parked")
data class ParkedPosition(
    @PrimaryKey val id: Int = 0,
    val time: Long,
    val lat: Double,
    val lon: Double,
    val accuracyM: Float? = null,
    val scooterName: String? = null,
    val soc: Int? = null,
    /** One of [ParkReason]; null for spots saved before reasons existed. */
    val reason: String? = null,
)
