package com.melonapp.android_nsw_parking_overlay.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class ParkingSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return ParkingListScreen(carContext)
    }
}
