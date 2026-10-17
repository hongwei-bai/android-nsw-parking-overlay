package com.melonapp.android_nsw_parking_overlay.auto

import android.content.pm.ApplicationInfo
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

import com.melonapp.android_nsw_parking_overlay.overlay.OverlayService

class ParkingCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator {
        return if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session {
        if (OverlayService.isOverlayPermissionGranted(this)) {
            OverlayService.start(this)
        }
        return ParkingSession()
    }
}
