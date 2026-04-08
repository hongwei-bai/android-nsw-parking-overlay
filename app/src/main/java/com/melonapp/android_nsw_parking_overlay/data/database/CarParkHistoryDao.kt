package com.melonapp.android_nsw_parking_overlay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
        ORDER BY queriedAtEpochMillis ASC
        """
    )
    fun observeHistoryForCarParks(
        carParkIds: List<String>,
        fromEpochMillis: Long
    ): Flow<List<CarParkHistoryRecord>>

    @Query("SELECT COUNT(*) FROM car_park_history")
    fun observeCount(): Flow<Int>
}
