package com.melonapp.android_nsw_parking_overlay.auto

import com.melonapp.android_nsw_parking_overlay.data.model.CarParkResponse

data class CarParkItem(
    val id: String,
    val name: String,
    val availableSpots: Int,
    val totalSpots: Int,
    val details: CarParkResponse? = null
)
