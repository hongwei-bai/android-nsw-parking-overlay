package com.melonapp.android_nsw_parking_overlay.data.repository

import com.melonapp.android_nsw_parking_overlay.data.api.TfNswApiService
import com.melonapp.android_nsw_parking_overlay.data.database.CarParkHistoryDao
import com.melonapp.android_nsw_parking_overlay.data.database.CarParkHistoryRecord
import com.melonapp.android_nsw_parking_overlay.data.model.CarParkResponse
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CarParkRepository(
    private val apiService: TfNswApiService,
    private val historyDao: CarParkHistoryDao
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault())
    private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault())

    /**
     * Fetches all available car park facilities.
     * @param apiKey The API key for TfNSW.
     * @return A map of facility ID to facility name.
     */
    suspend fun getAllFacilities(apiKey: String): Map<String, String> {
        val auth = "apikey $apiKey"
        val response = apiService.getAllCarParks(auth)
        return if (response.isSuccessful) {
            response.body() ?: emptyMap()
        } else {
            emptyMap()
        }
    }

    /**
     * Fetches detailed information for a specific car park facility.
     * @param apiKey The API key for TfNSW.
     * @param facilityId The ID of the facility.
     */
    suspend fun getCarParkDetails(apiKey: String, facilityId: String): CarParkResponse? {
        val auth = "apikey $apiKey"
        val response = apiService.getCarParkById(auth, facilityId)
        return if (response.isSuccessful) {
            response.body()
        } else {
            null
        }
    }

    suspend fun getCarParkDetailsAndRecord(
        apiKey: String,
        facilityId: String,
        fallbackName: String
    ): CarParkResponse? {
        val details = getCarParkDetails(apiKey, facilityId)
        if (details != null) {
            historyDao.insert(
                details.toHistoryRecord(fallbackName = fallbackName)
            )
        }
        return details
    }

    fun observeHistoryCount(): Flow<Int> = historyDao.observeCount()

    private fun CarParkResponse.toHistoryRecord(fallbackName: String): CarParkHistoryRecord {
        val now = Instant.now().atZone(ZoneId.systemDefault())
        return CarParkHistoryRecord(
            carParkId = facilityId,
            carParkName = facilityName.orEmpty().ifBlank { fallbackName },
            queryDate = dateFormatter.format(now),
            queryWeekday = weekdayFormatter.format(now),
            queryTime = timeFormatter.format(now),
            queriedAtEpochMillis = now.toInstant().toEpochMilli(),
            spaceLeft = availableSpots
        )
    }
}
