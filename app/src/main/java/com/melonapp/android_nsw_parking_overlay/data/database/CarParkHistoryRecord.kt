package com.melonapp.android_nsw_parking_overlay.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "car_park_history",
    indices = [
        Index(
            value = ["carParkId", "queriedAtEpochMillis", "spaceLeft"],
            unique = true
        )
    ]
)
data class CarParkHistoryRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val carParkId: String,
    val carParkName: String,
    val queryDate: String,
    val queryWeekday: String,
    val queryTime: String,
    val queriedAtEpochMillis: Long,
    val spaceLeft: Int,
    val isPublicHolidayNsw: Boolean = false,
    val isFirstWeekOfMonth: Boolean = false,
    val isLastWeekOfMonth: Boolean = false
)
