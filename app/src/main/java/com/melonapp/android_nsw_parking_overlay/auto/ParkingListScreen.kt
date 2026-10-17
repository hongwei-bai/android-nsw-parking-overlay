package com.melonapp.android_nsw_parking_overlay.auto

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.melonapp.android_nsw_parking_overlay.data.DataStoreManager
import com.melonapp.android_nsw_parking_overlay.data.api.RetrofitClient
import com.melonapp.android_nsw_parking_overlay.data.database.AppDatabase
import com.melonapp.android_nsw_parking_overlay.data.repository.CarParkRepository
import com.melonapp.android_nsw_parking_overlay.ui.SelectedCarPark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParkingListScreen(carContext: CarContext) : Screen(carContext) {

    private var isLoading: Boolean = true
    private var errorMessage: String? = null
    private var carParks: List<CarParkItem> = emptyList()
    private var lowThreshold: Int = 10
    private var midThreshold: Int = 30

    private val gson = Gson()
    private val dataStoreManager by lazy { DataStoreManager(carContext) }
    private val repository by lazy {
        CarParkRepository(
            apiService = RetrofitClient.apiService,
            historyDao = AppDatabase.getInstance(carContext).carParkHistoryDao()
        )
    }

    init {
        loadCarParks()
    }

    private fun loadCarParks() {
        isLoading = true
        errorMessage = null
        invalidate()

        lifecycleScope.launch {
            try {
                val apiKey = dataStoreManager.apiKey.first().orEmpty()
                if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
                    errorMessage = "API key not configured.\nPlease configure your TfNSW API key in the phone app."
                    isLoading = false
                    invalidate()
                    return@launch
                }

                lowThreshold = runCatching { dataStoreManager.overlayThresholdLow.first() }.getOrDefault(10)
                midThreshold = runCatching { dataStoreManager.overlayThresholdMid.first() }.getOrDefault(30)

                val contentLimit = runCatching {
                    carContext.getCarService(ConstraintManager::class.java)
                        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
                }.getOrDefault(6).coerceAtLeast(6)

                val selectedJson = dataStoreManager.selectedCarParks.first().orEmpty()
                val selectedList: List<SelectedCarPark> = if (selectedJson.isNotBlank()) {
                    val type = object : TypeToken<List<SelectedCarPark>>() {}.type
                    runCatching { gson.fromJson<List<SelectedCarPark>>(selectedJson, type) }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                val items: List<CarParkItem> = withContext(Dispatchers.IO) {
                    if (selectedList.isNotEmpty()) {
                        selectedList.take(contentLimit).map { sp ->
                            val details = repository.getCarParkDetailsAndRecord(
                                apiKey = apiKey,
                                facilityId = sp.id,
                                fallbackName = sp.name
                            )
                            val total = details?.totalSpots?.toIntOrNull() ?: 0
                            val available = details?.availableSpots ?: sp.availableSpots
                            CarParkItem(
                                id = sp.id,
                                name = sp.name,
                                availableSpots = available,
                                totalSpots = total,
                                details = details
                            )
                        }
                    } else {
                        // Fallback: fetch all facilities and query the top ones
                        val allFacilities = repository.getAllFacilities(apiKey)
                        allFacilities.entries.take(contentLimit).map { (facilityId, facilityName) ->
                            val details = repository.getCarParkDetailsAndRecord(
                                apiKey = apiKey,
                                facilityId = facilityId,
                                fallbackName = facilityName
                            )
                            val total = details?.totalSpots?.toIntOrNull() ?: 0
                            val available = details?.availableSpots ?: 0
                            CarParkItem(
                                id = facilityId,
                                name = facilityName,
                                availableSpots = available,
                                totalSpots = total,
                                details = details
                            )
                        }
                    }
                }

                carParks = items
                isLoading = false
                invalidate()
            } catch (_: Exception) {
                errorMessage = "Failed to load parking data. Check connection."
                isLoading = false
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val appHeader = Header.Builder()
            .setTitle("NSW Parking")
            .setStartHeaderAction(Action.APP_ICON)
            .build()

        if (isLoading) {
            return ListTemplate.Builder()
                .setHeader(appHeader)
                .setLoading(true)
                .build()
        }

        val err = errorMessage
        if (err != null) {
            return MessageTemplate.Builder(err)
                .setHeader(appHeader)
                .addAction(
                    Action.Builder()
                        .setTitle("Retry")
                        .setOnClickListener { loadCarParks() }
                        .build()
                )
                .build()
        }

        if (carParks.isEmpty()) {
            return MessageTemplate.Builder("No car parks configured.\nSelect car parks in the phone app.")
                .setHeader(appHeader)
                .addAction(
                    Action.Builder()
                        .setTitle("Refresh")
                        .setOnClickListener { loadCarParks() }
                        .build()
                )
                .build()
        }

        val itemListBuilder = ItemList.Builder()
        for (item in carParks) {
            val spots = item.availableSpots
            val total = item.totalSpots
            val spotsText = if (total > 0) {
                "$spots / $total spots free"
            } else {
                "$spots spots free"
            }

            val color = when {
                spots == 0 -> CarColor.RED
                spots <= lowThreshold -> CarColor.RED
                spots <= midThreshold -> CarColor.YELLOW
                else -> CarColor.GREEN
            }

            val spannableSpots = SpannableString(spotsText).apply {
                setSpan(ForegroundCarColorSpan.create(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            val rowBuilder = Row.Builder()
                .setTitle(item.name)
                .addText(spannableSpots)

            val suburb = item.details?.location?.suburb
            if (!suburb.isNullOrBlank()) {
                rowBuilder.addText(suburb)
            }

            rowBuilder.setBrowsable(true)
            rowBuilder.setOnClickListener {
                screenManager.push(
                    CarParkDetailScreen(
                        carContext = carContext,
                        item = item,
                        lowThreshold = lowThreshold,
                        midThreshold = midThreshold
                    )
                )
            }

            itemListBuilder.addItem(rowBuilder.build())
        }

        val listHeader = Header.Builder()
            .setTitle("NSW Parking")
            .setStartHeaderAction(Action.APP_ICON)
            .addEndHeaderAction(
                Action.Builder()
                    .setTitle("Refresh")
                    .setOnClickListener { loadCarParks() }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setHeader(listHeader)
            .setSingleList(itemListBuilder.build())
            .build()
    }
}
