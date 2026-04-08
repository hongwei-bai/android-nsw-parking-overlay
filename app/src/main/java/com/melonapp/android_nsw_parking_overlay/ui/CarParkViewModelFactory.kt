package com.melonapp.android_nsw_parking_overlay.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.melonapp.android_nsw_parking_overlay.data.DataStoreManager
import com.melonapp.android_nsw_parking_overlay.data.database.HistoryBackupManager
import com.melonapp.android_nsw_parking_overlay.data.repository.CarParkRepository

class CarParkViewModelFactory(
    private val repository: CarParkRepository,
    private val dataStoreManager: DataStoreManager,
    private val historyBackupManager: HistoryBackupManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CarParkViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CarParkViewModel(repository, dataStoreManager, historyBackupManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
