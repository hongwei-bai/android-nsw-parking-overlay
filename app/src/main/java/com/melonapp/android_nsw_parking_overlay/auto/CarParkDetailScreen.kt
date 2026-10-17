package com.melonapp.android_nsw_parking_overlay.auto

import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.melonapp.android_nsw_parking_overlay.data.DataStoreManager
import com.melonapp.android_nsw_parking_overlay.data.api.RetrofitClient
import com.melonapp.android_nsw_parking_overlay.data.database.AppDatabase
import com.melonapp.android_nsw_parking_overlay.data.repository.CarParkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CarParkDetailScreen(
    carContext: CarContext,
    private val item: CarParkItem,
    private val lowThreshold: Int,
    private val midThreshold: Int
) : Screen(carContext) {

    private var currentItem: CarParkItem = item
    private var isRefreshing: Boolean = false

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        // Row 1: Available Spots
        val spots = currentItem.availableSpots
        val color = when {
            spots == 0 -> CarColor.RED
            spots <= lowThreshold -> CarColor.RED
            spots <= midThreshold -> CarColor.YELLOW
            else -> CarColor.GREEN
        }
        val spotsText = "$spots spots available"
        val spannableSpots = SpannableString(spotsText).apply {
            setSpan(ForegroundCarColorSpan.create(color), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Available Spots")
                .addText(spannableSpots)
                .build()
        )

        // Row 2: Capacity / Occupancy
        val total = currentItem.totalSpots
        if (total > 0) {
            val occupied = (total - spots).coerceAtLeast(0)
            val percentage = ((occupied.toDouble() / total) * 100).toInt()
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Capacity")
                    .addText("$total spots total • $percentage% occupied")
                    .build()
            )
        } else {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Capacity")
                    .addText("Total spots: $total")
                    .build()
            )
        }

        // Row 3: Location
        val location = currentItem.details?.location
        val addressParts = listOfNotNull(
            location?.address?.takeIf { it.isNotBlank() },
            location?.suburb?.takeIf { it.isNotBlank() }
        )
        val locationString = if (addressParts.isNotEmpty()) {
            addressParts.joinToString(", ")
        } else {
            "NSW, Australia"
        }
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Location")
                .addText(locationString)
                .build()
        )

        // Row 4: Last Updated
        val time = currentItem.details?.time ?: currentItem.details?.messageDate ?: "Live"
        paneBuilder.addRow(
            Row.Builder()
                .setTitle("Last Updated")
                .addText(time)
                .build()
        )

        // Primary Action: Navigate
        val navigateAction = Action.Builder()
            .setTitle("Navigate")
            .setOnClickListener {
                startNavigation()
            }
            .build()
        paneBuilder.addAction(navigateAction)

        // Secondary Action: Refresh
        val refreshAction = Action.Builder()
            .setTitle(if (isRefreshing) "Refreshing..." else "Refresh")
            .setOnClickListener {
                refreshDetails()
            }
            .build()
        paneBuilder.addAction(refreshAction)

        val header = Header.Builder()
            .setTitle(currentItem.name)
            .setStartHeaderAction(Action.BACK)
            .build()

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(header)
            .build()
    }

    private fun startNavigation() {
        val lat = currentItem.details?.location?.latitude?.toDoubleOrNull()
        val lon = currentItem.details?.location?.longitude?.toDoubleOrNull()
        val uri = if (lat != null && lon != null) {
            Uri.parse("geo:$lat,$lon?q=${Uri.encode(currentItem.name)}")
        } else {
            val suburb = currentItem.details?.location?.suburb.orEmpty()
            Uri.parse("geo:0,0?q=${Uri.encode("${currentItem.name} $suburb NSW Australia")}")
        }

        val navIntent = Intent(CarContext.ACTION_NAVIGATE, uri)
        try {
            carContext.startCarApp(navIntent)
        } catch (_: Exception) {
            CarToast.makeText(carContext, "No navigation app found", CarToast.LENGTH_SHORT).show()
        }
    }

    private fun refreshDetails() {
        if (isRefreshing) return
        isRefreshing = true
        invalidate()

        lifecycleScope.launch {
            try {
                val dataStore = DataStoreManager(carContext)
                val apiKey = dataStore.apiKey.first().orEmpty()
                if (apiKey.isNotBlank()) {
                    val db = AppDatabase.getInstance(carContext)
                    val repo = CarParkRepository(RetrofitClient.apiService, db.carParkHistoryDao())
                    val newDetails = withContext(Dispatchers.IO) {
                        repo.getCarParkDetailsAndRecord(apiKey, currentItem.id, currentItem.name)
                    }
                    if (newDetails != null) {
                        val total = newDetails.totalSpots?.toIntOrNull() ?: currentItem.totalSpots
                        currentItem = currentItem.copy(
                            availableSpots = newDetails.availableSpots,
                            totalSpots = total,
                            details = newDetails
                        )
                        CarToast.makeText(carContext, "Updated", CarToast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                CarToast.makeText(carContext, "Update failed", CarToast.LENGTH_SHORT).show()
            } finally {
                isRefreshing = false
                invalidate()
            }
        }
    }
}
