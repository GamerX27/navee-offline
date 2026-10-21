package com.xlabsx27.naveeoffline.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert suspend fun insert(trip: Trip): Long
    @Update suspend fun update(trip: Trip)
    @Query("SELECT * FROM trips WHERE id = :id") suspend fun get(id: Long): Trip?
    @Query("SELECT * FROM trips WHERE id = :id") fun observe(id: Long): Flow<Trip?>
    @Query("SELECT * FROM trips ORDER BY startedAt DESC") fun observeAll(): Flow<List<Trip>>
    @Query("SELECT * FROM trips WHERE startedAt >= :from AND startedAt < :to ORDER BY startedAt")
    fun observeBetween(from: Long, to: Long): Flow<List<Trip>>
    /** Local calendar days (yyyy-MM-dd) that have at least one trip. */
    @Query("SELECT DISTINCT date(startedAt / 1000, 'unixepoch', 'localtime') AS day FROM trips ORDER BY day")
    fun observeDays(): Flow<List<String>>
    @Query("DELETE FROM trips WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM trips WHERE endedAt IS NULL") suspend fun unfinished(): List<Trip>

    @Insert suspend fun insertPoint(point: TrackPoint)
    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY time") suspend fun points(tripId: Long): List<TrackPoint>
    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY time") fun observePoints(tripId: Long): Flow<List<TrackPoint>>
    @Query("SELECT * FROM track_points WHERE tripId IN (:tripIds) ORDER BY tripId, time")
    fun observePointsForTrips(tripIds: List<Long>): Flow<List<TrackPoint>>
    @Query("SELECT * FROM track_points ORDER BY tripId, time") fun observeAllPoints(): Flow<List<TrackPoint>>
    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY time DESC LIMIT 1") suspend fun lastPoint(tripId: Long): TrackPoint?
}

@Dao
interface ParkedDao {
    @Upsert suspend fun save(p: ParkedPosition)
    @Query("SELECT * FROM parked WHERE id = 0") fun observe(): Flow<ParkedPosition?>
    @Query("DELETE FROM parked") suspend fun clear()
}
