package com.melonapp.android_nsw_parking_overlay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CarParkHistoryBounds(
    val minEpochMillis: Long?,
    val maxEpochMillis: Long?
)

@Dao
interface CarParkHistoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: CarParkHistoryRecord)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<CarParkHistoryRecord>)

    @Query("SELECT * FROM car_park_history ORDER BY queriedAtEpochMillis DESC")
    suspend fun getAll(): List<CarParkHistoryRecord>

    @Query(
        """
        SELECT * FROM car_park_history
        WHERE carParkId IN (:carParkIds)
          AND queriedAtEpochMillis >= :fromEpochMillis
          AND queriedAtEpochMillis <= :toEpochMillis
        ORDER BY queriedAtEpochMillis ASC
        """
    )
    fun observeHistoryForCarParks(
        carParkIds: List<String>,
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): Flow<List<CarParkHistoryRecord>>

    @Query(
        """
        SELECT
            MIN(queriedAtEpochMillis) AS minEpochMillis,
            MAX(queriedAtEpochMillis) AS maxEpochMillis
        FROM car_park_history
        WHERE carParkId IN (:carParkIds)
        """
    )
    fun observeHistoryBounds(carParkIds: List<String>): Flow<CarParkHistoryBounds>

    @Query(
        """
        SELECT COUNT(*) FROM car_park_history
        WHERE carParkId IN (:carParkIds)
          AND queriedAtEpochMillis >= :fromEpochMillis
          AND queriedAtEpochMillis <= :toEpochMillis
        """
    )
    suspend fun countHistoryInRange(
        carParkIds: List<String>,
        fromEpochMillis: Long,
        toEpochMillis: Long
    ): Int

    @Query("SELECT COUNT(*) FROM car_park_history")
    fun observeCount(): Flow<Int>
}
