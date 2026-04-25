package com.melonapp.android_nsw_parking_overlay.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.melonapp.android_nsw_parking_overlay.data.DataStoreManager
import com.melonapp.android_nsw_parking_overlay.data.database.CarParkHistoryRecord
import com.melonapp.android_nsw_parking_overlay.data.database.HistoryBackupManager
import com.melonapp.android_nsw_parking_overlay.data.model.CarParkResponse
import com.melonapp.android_nsw_parking_overlay.data.repository.CarParkRepository
import com.melonapp.android_nsw_parking_overlay.util.CarParkUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

enum class HistoryTimespanPreset(
    val label: String,
    val duration: Duration,
    val isLongSpan: Boolean
) {
    FIFTEEN_MINUTES("15m", Duration.ofMinutes(15), false),
    ONE_HOUR("1h", Duration.ofHours(1), false),
    FOUR_HOURS("4h", Duration.ofHours(4), false),
    ONE_DAY("1d", Duration.ofDays(1), false),
    ONE_WEEK("1w", Duration.ofDays(7), true),
    ONE_MONTH("1m", Duration.ofDays(30), true),
    ONE_QUARTER("1q", Duration.ofDays(91), true),
    ONE_YEAR("1y", Duration.ofDays(365), true)
}

data class SelectedCarPark(
    val id: String,
    val name: String,
    val abbr: String,
    val availableSpots: Int = 0,
    val smartUnavailableDetectionEnabled: Boolean? = true
)

data class HistoryPoint(
    val epochMillis: Long,
    val spacesLeft: Int
)

data class HistorySeries(
    val carParkId: String,
    val carParkName: String,
    val colorArgb: Int,
    val smartUnavailableDetectionEnabled: Boolean,
    val points: List<HistoryPoint>
)

data class CarParkUiState(
    val isLoading: Boolean = false,
    val facilities: Map<String, String> = emptyMap(),
    val selectedFacilityDetails: CarParkResponse? = null,
    val selectedCarParks: List<SelectedCarPark> = emptyList(),
    val errorMessage: String? = null,
    val hasOverlayPermission: Boolean = false,
    val apiKey: String = "",
    val historyCount: Int = 0,
    val historyStatusMessage: String? = null,
    val isHistoryOperationInProgress: Boolean = false,
    val historyTimespanPreset: HistoryTimespanPreset = HistoryTimespanPreset.ONE_HOUR,
    val historySeries: List<HistorySeries> = emptyList(),
    val historyWindowEndEpochMillis: Long = Instant.now().toEpochMilli(),
    val historyMinEpochMillis: Long? = null,
    val historyMaxEpochMillis: Long? = null,

    val overlayRefreshIntervalMs: Long = 30_000L,
    val overlayThresholdLow: Int = 10,
    val overlayThresholdMid: Int = 30,
    val overlayColorRedArgb: Int = 0xFFFF3B30.toInt(),
    val overlayColorOrangeArgb: Int = 0xFFFF9500.toInt(),
    val overlayColorGreenArgb: Int = 0xFF34C759.toInt(),

    val silentQueryMode: Boolean = false,
    val silentQueryIntervalMs: Long = 120_000L
)

class CarParkViewModel(
    private val repository: CarParkRepository,
    private val dataStoreManager: DataStoreManager,
    private val historyBackupManager: HistoryBackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CarParkUiState())
    val uiState: StateFlow<CarParkUiState> = _uiState.asStateFlow()

    private val gson = Gson()
    private var historyJob: Job? = null
    private var historyBoundsJob: Job? = null
    private var historyShiftJob: Job? = null

    init {
        observeDataStore()
        observeHistoryCount()
    }

    private fun observeDataStore() {
        viewModelScope.launch {
            dataStoreManager.apiKey.collectLatest { key ->
                _uiState.update { it.copy(apiKey = key ?: "") }
            }
        }
        viewModelScope.launch {
            dataStoreManager.selectedCarParks.collectLatest { json ->
                if (!json.isNullOrBlank()) {
                    val type = object : TypeToken<List<SelectedCarPark>>() {}.type
                    val list: List<SelectedCarPark> = gson.fromJson<List<SelectedCarPark>>(json, type)
                        .map { selected ->
                            selected.copy(
                                smartUnavailableDetectionEnabled =
                                    selected.smartUnavailableDetectionEnabled ?: true
                            )
                        }
                    _uiState.update { it.copy(selectedCarParks = list) }
                    observeHistoryBounds(list)
                    observeHistorySeries(
                        selectedCarParks = list,
                        preset = _uiState.value.historyTimespanPreset,
                        windowEndEpochMillis = _uiState.value.historyWindowEndEpochMillis
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            selectedCarParks = emptyList(),
                            historyMinEpochMillis = null,
                            historyMaxEpochMillis = null,
                            historySeries = emptyList()
                        )
                    }
                    historyBoundsJob?.cancel()
                    observeHistorySeries(
                        emptyList(),
                        _uiState.value.historyTimespanPreset,
                        _uiState.value.historyWindowEndEpochMillis
                    )
                }
            }
        }
        viewModelScope.launch {
            dataStoreManager.overlayRefreshIntervalMs.collectLatest { value ->
                _uiState.update { it.copy(overlayRefreshIntervalMs = value) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.overlayThresholdLow.collectLatest { value ->
                _uiState.update { it.copy(overlayThresholdLow = value) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.overlayThresholdMid.collectLatest { value ->
                _uiState.update { it.copy(overlayThresholdMid = value) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.overlayColorRed.collectLatest { value ->
                _uiState.update { it.copy(overlayColorRedArgb = value) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.overlayColorOrange.collectLatest { value ->
                _uiState.update { it.copy(overlayColorOrangeArgb = value) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.overlayColorGreen.collectLatest { value ->
                _uiState.update { it.copy(overlayColorGreenArgb = value) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.silentQueryMode.collectLatest { value ->
                _uiState.update { it.copy(silentQueryMode = value) }
            }
        }
        viewModelScope.launch {
            dataStoreManager.silentQueryIntervalMs.collectLatest { value ->
                _uiState.update { it.copy(silentQueryIntervalMs = value) }
            }
        }
    }

    private fun observeHistoryCount() {
        viewModelScope.launch {
            repository.observeHistoryCount().collectLatest { count ->
                _uiState.update { it.copy(historyCount = count) }
            }
        }
    }

    private fun observeHistorySeries(
        selectedCarParks: List<SelectedCarPark>,
        preset: HistoryTimespanPreset,
        windowEndEpochMillis: Long
    ) {
        historyJob?.cancel()
        if (selectedCarParks.isEmpty()) {
            _uiState.update { it.copy(historySeries = emptyList()) }
            return
        }

        val colorPalette = listOf(
            0xFF1E88E5.toInt(),
            0xFFD81B60.toInt(),
            0xFF43A047.toInt()
        )
        val selectedById = selectedCarParks.associateBy { it.id }
        val fromEpochMillis = windowEndEpochMillis - preset.duration.toMillis()

        historyJob = viewModelScope.launch {
            repository.observeHistoryForCarParks(
                carParkIds = selectedCarParks.map { it.id },
                fromEpochMillis = fromEpochMillis,
                toEpochMillis = windowEndEpochMillis
            ).collectLatest { records ->
                val series = records
                    .groupBy { it.carParkId }
                    .mapNotNull { (carParkId, carParkRecords) ->
                        val selected = selectedById[carParkId] ?: return@mapNotNull null
                        val sorted = carParkRecords.sortedBy { it.queriedAtEpochMillis }
                        HistorySeries(
                            carParkId = carParkId,
                            carParkName = selected.name,
                            colorArgb = colorPalette[
                                selectedCarParks.indexOfFirst { it.id == carParkId }
                                    .coerceAtLeast(0) % colorPalette.size
                            ],
                            smartUnavailableDetectionEnabled =
                                selected.smartUnavailableDetectionEnabled != false,
                            points = sorted.map { it.toHistoryPoint() }
                        )
                    }
                    .sortedBy { seriesItem ->
                        selectedCarParks.indexOfFirst { it.id == seriesItem.carParkId }
                    }
                _uiState.update { it.copy(historySeries = series) }
            }
        }
    }

    private fun observeHistoryBounds(selectedCarParks: List<SelectedCarPark>) {
        historyBoundsJob?.cancel()
        if (selectedCarParks.isEmpty()) return

        historyBoundsJob = viewModelScope.launch {
            repository.observeHistoryBounds(selectedCarParks.map { it.id }).collectLatest { bounds ->
                val maxEpoch = bounds.maxEpochMillis ?: Instant.now().toEpochMilli()
                _uiState.update { current ->
                    val currentEnd = current.historyWindowEndEpochMillis
                    val nextEnd = when {
                        current.historyMaxEpochMillis == null -> maxEpoch
                        currentEnd > maxEpoch -> maxEpoch
                        else -> currentEnd
                    }
                    current.copy(
                        historyMinEpochMillis = bounds.minEpochMillis,
                        historyMaxEpochMillis = bounds.maxEpochMillis,
                        historyWindowEndEpochMillis = nextEnd
                    )
                }
                observeHistorySeries(
                    selectedCarParks = _uiState.value.selectedCarParks,
                    preset = _uiState.value.historyTimespanPreset,
                    windowEndEpochMillis = _uiState.value.historyWindowEndEpochMillis
                )
            }
        }
    }

    fun setHistoryTimespanPreset(preset: HistoryTimespanPreset) {
        val latestEnd = _uiState.value.historyMaxEpochMillis ?: Instant.now().toEpochMilli()
        _uiState.update {
            it.copy(
                historyTimespanPreset = preset,
                historyWindowEndEpochMillis = latestEnd
            )
        }
        observeHistorySeries(_uiState.value.selectedCarParks, preset, latestEnd)
    }

    fun shiftHistoryWindow(direction: Int) {
        val state = _uiState.value
        val minEpoch = state.historyMinEpochMillis ?: return
        val maxEpoch = state.historyMaxEpochMillis ?: return
        val spanMillis = state.historyTimespanPreset.duration.toMillis()
        val minAllowedEnd = minOf(minEpoch + spanMillis, maxEpoch)
        val maxAllowedEnd = maxOf(minAllowedEnd, maxEpoch)
        historyShiftJob?.cancel()
        historyShiftJob = viewModelScope.launch {
            var candidateEnd = state.historyWindowEndEpochMillis + (spanMillis * direction)
            while (candidateEnd in minAllowedEnd..maxAllowedEnd) {
                val fromEpoch = candidateEnd - spanMillis
                val count = repository.countHistoryInRange(
                    carParkIds = state.selectedCarParks.map { it.id },
                    fromEpochMillis = fromEpoch,
                    toEpochMillis = candidateEnd
                )
                if (count > 0) {
                    _uiState.update { it.copy(historyWindowEndEpochMillis = candidateEnd) }
                    observeHistorySeries(
                        selectedCarParks = state.selectedCarParks,
                        preset = state.historyTimespanPreset,
                        windowEndEpochMillis = candidateEnd
                    )
                    return@launch
                }
                candidateEnd += spanMillis * direction
            }
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            dataStoreManager.saveApiKey(key)
        }
    }

    fun setOverlayRefreshIntervalMs(value: Long) {
        viewModelScope.launch {
            dataStoreManager.saveOverlayRefreshIntervalMs(value)
        }
    }

    fun setOverlayThresholds(low: Int, mid: Int) {
        viewModelScope.launch {
            dataStoreManager.saveOverlayThresholds(low = low, mid = mid)
        }
    }

    fun setOverlayColors(redArgb: Int, orangeArgb: Int, greenArgb: Int) {
        viewModelScope.launch {
            dataStoreManager.saveOverlayColors(red = redArgb, orange = orangeArgb, green = greenArgb)
        }
    }

    fun setOverlayPermission(hasPermission: Boolean) {
        _uiState.update { it.copy(hasOverlayPermission = hasPermission) }
    }

    fun setSilentMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.saveSilentQueryMode(enabled)
        }
    }

    fun setSilentQueryIntervalMs(value: Long) {
        viewModelScope.launch {
            dataStoreManager.saveSilentQueryIntervalMs(value)
        }
    }

    fun fetchFacilities() {
        val apiKey = _uiState.value.apiKey
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
            _uiState.update { it.copy(errorMessage = "API Key is missing") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val facilities = repository.getAllFacilities(apiKey)
                if (facilities.isNotEmpty()) {
                    _uiState.update { it.copy(facilities = facilities, isLoading = false) }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to fetch facilities") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun toggleCarParkSelection(id: String, name: String) {
        val currentSelected = _uiState.value.selectedCarParks.toMutableList()
        val existing = currentSelected.find { it.id == id }
        
        if (existing != null) {
            currentSelected.remove(existing)
        } else {
            if (currentSelected.size < 3) {
                currentSelected.add(
                    SelectedCarPark(
                        id = id,
                        name = name,
                        abbr = CarParkUtils.getAbbreviation(name),
                        smartUnavailableDetectionEnabled = true
                    )
                )
            }
        }
        
        viewModelScope.launch {
            dataStoreManager.saveSelectedCarParks(gson.toJson(currentSelected))
        }
    }

    fun setSmartUnavailableDetection(id: String, enabled: Boolean) {
        val updated = _uiState.value.selectedCarParks.map { selected ->
            if (selected.id == id) {
                selected.copy(smartUnavailableDetectionEnabled = enabled)
            } else {
                selected
            }
        }
        _uiState.update { it.copy(selectedCarParks = updated) }
        observeHistorySeries(
            selectedCarParks = updated,
            preset = _uiState.value.historyTimespanPreset,
            windowEndEpochMillis = _uiState.value.historyWindowEndEpochMillis
        )
        viewModelScope.launch {
            dataStoreManager.saveSelectedCarParks(gson.toJson(updated))
        }
    }

    fun refreshSelectedCarParks() {
        val apiKey = _uiState.value.apiKey
        if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") return

        viewModelScope.launch {
            val currentSelected = _uiState.value.selectedCarParks
            val updatedList = currentSelected.map { selected ->
                val details = repository.getCarParkDetailsAndRecord(apiKey, selected.id, selected.name)
                selected.copy(availableSpots = details?.availableSpots ?: 0)
            }
            _uiState.update { it.copy(selectedCarParks = updatedList) }
            // Note: We don't necessarily need to save back to DataStore here 
            // because spots change frequently, but we might want to if we want the widget 
            // to show the last fetched value. However, the widget fetches its own data.
        }
    }

    fun exportHistory(contentResolver: ContentResolver, treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isHistoryOperationInProgress = true,
                    historyStatusMessage = null
                )
            }
            val message = runCatching {
                val fileName = historyBackupManager.exportToTree(contentResolver, treeUri)
                "Exported history to $fileName"
            }.getOrElse { error ->
                error.message ?: "Export failed"
            }
            _uiState.update {
                it.copy(
                    isHistoryOperationInProgress = false,
                    historyStatusMessage = message
                )
            }
        }
    }

    fun importHistory(contentResolver: ContentResolver, fileUri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isHistoryOperationInProgress = true,
                    historyStatusMessage = null
                )
            }
            val message = runCatching {
                val imported = historyBackupManager.importFromFile(contentResolver, fileUri)
                "Imported $imported history records"
            }.getOrElse { error ->
                error.message ?: "Import failed"
            }
            _uiState.update {
                it.copy(
                    isHistoryOperationInProgress = false,
                    historyStatusMessage = message
                )
            }
        }
    }

    private fun CarParkHistoryRecord.toHistoryPoint(): HistoryPoint {
        return HistoryPoint(
            epochMillis = queriedAtEpochMillis,
            spacesLeft = spaceLeft
        )
    }
}
