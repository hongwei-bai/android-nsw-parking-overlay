package com.melonapp.android_nsw_parking_overlay.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "parking_prefs")

class DataStoreManager(private val context: Context) {

    companion object {
        val API_KEY = stringPreferencesKey("api_key")
        val SELECTED_CAR_PARKS = stringPreferencesKey("selected_car_parks")

        val OVERLAY_REFRESH_INTERVAL_MS = longPreferencesKey("overlay_refresh_interval_ms")
        val OVERLAY_THRESHOLD_LOW = intPreferencesKey("overlay_threshold_low")
        val OVERLAY_THRESHOLD_MID = intPreferencesKey("overlay_threshold_mid")

        // Stored as ARGB ints.
        val OVERLAY_COLOR_RED = intPreferencesKey("overlay_color_red")
        val OVERLAY_COLOR_ORANGE = intPreferencesKey("overlay_color_orange")
        val OVERLAY_COLOR_GREEN = intPreferencesKey("overlay_color_green")

        val SILENT_QUERY_MODE = booleanPreferencesKey("silent_query_mode")
        val SILENT_QUERY_INTERVAL_MS = longPreferencesKey("silent_query_interval_ms")
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[API_KEY]
    }

    val selectedCarParks: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[SELECTED_CAR_PARKS]
    }

    val overlayRefreshIntervalMs: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_REFRESH_INTERVAL_MS] ?: 30_000L
    }

    val silentQueryMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SILENT_QUERY_MODE] ?: false
    }

    val silentQueryIntervalMs: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[SILENT_QUERY_INTERVAL_MS] ?: 120_000L
    }

    val overlayThresholdLow: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_THRESHOLD_LOW] ?: 10
    }

    val overlayThresholdMid: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_THRESHOLD_MID] ?: 30
    }

    val overlayColorRed: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_COLOR_RED] ?: 0xFFFF3B30.toInt()
    }

    val overlayColorOrange: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_COLOR_ORANGE] ?: 0xFFFF9500.toInt()
    }

    val overlayColorGreen: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[OVERLAY_COLOR_GREEN] ?: 0xFF34C759.toInt()
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = key
        }
    }

    suspend fun saveSelectedCarParks(json: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_CAR_PARKS] = json
        }
    }

    suspend fun saveOverlayRefreshIntervalMs(value: Long) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_REFRESH_INTERVAL_MS] = value
        }
    }

    suspend fun saveOverlayThresholds(low: Int, mid: Int) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_THRESHOLD_LOW] = low
            preferences[OVERLAY_THRESHOLD_MID] = mid
        }
    }

    suspend fun saveOverlayColors(red: Int, orange: Int, green: Int) {
        context.dataStore.edit { preferences ->
            preferences[OVERLAY_COLOR_RED] = red
            preferences[OVERLAY_COLOR_ORANGE] = orange
            preferences[OVERLAY_COLOR_GREEN] = green
        }
    }

    suspend fun saveSilentQueryMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SILENT_QUERY_MODE] = enabled
        }
    }

    suspend fun saveSilentQueryIntervalMs(value: Long) {
        context.dataStore.edit { preferences ->
            preferences[SILENT_QUERY_INTERVAL_MS] = value
        }
    }
}
