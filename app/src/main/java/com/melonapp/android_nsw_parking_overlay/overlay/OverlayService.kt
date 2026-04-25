package com.melonapp.android_nsw_parking_overlay.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import android.view.View
import android.widget.ProgressBar
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.melonapp.android_nsw_parking_overlay.MainActivity
import com.melonapp.android_nsw_parking_overlay.R
import com.melonapp.android_nsw_parking_overlay.data.DataStoreManager
import com.melonapp.android_nsw_parking_overlay.data.api.RetrofitClient
import com.melonapp.android_nsw_parking_overlay.data.database.AppDatabase
import com.melonapp.android_nsw_parking_overlay.data.repository.CarParkRepository
import com.melonapp.android_nsw_parking_overlay.ui.SelectedCarPark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var isSilentMode: Boolean = false
    private lateinit var params: WindowManager.LayoutParams

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main.immediate + serviceJob)

    // Variables for dragging
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private val gson = Gson()
    private val repository by lazy {
        CarParkRepository(
            apiService = RetrofitClient.apiService,
            historyDao = AppDatabase.getInstance(applicationContext).carParkHistoryDao()
        )
    }
    private val dataStoreManager by lazy { DataStoreManager(applicationContext) }

    private var lastGood: List<SelectedCarPark> = emptyList()
    private var isManualRefreshInFlight: Boolean = false

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "overlay_service_channel"
        const val NOTIFICATION_ID = 12345

        const val ACTION_START = "com.melonapp.android_nsw_parking_overlay.overlay.action.START"
        const val ACTION_STOP = "com.melonapp.android_nsw_parking_overlay.overlay.action.STOP"
        private const val DEFAULT_REFRESH_INTERVAL_MS = 30_000L

        fun isOverlayPermissionGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                // continue
            }
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate() {
        super.onCreate()

        if (!isOverlayPermissionGranted(this)) {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show()
            stopSelf()
            return
        }

        startForegroundServiceNotification()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        serviceScope.launch {
            isSilentMode = dataStoreManager.silentQueryMode.first()
            setupOverlay()
            updateLoop()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        // Remove existing view if any
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
        }

        val layoutRes = if (isSilentMode) R.layout.silent_overlay_layout else R.layout.overlay_layout
        overlayView = LayoutInflater.from(this).inflate(layoutRes, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 100

        windowManager.addView(overlayView, params)

        if (isSilentMode) {
            val dot = overlayView?.findViewById<View>(R.id.silent_dot)
            val flashAnim = AnimationUtils.loadAnimation(this, R.anim.slow_flash)
            dot?.startAnimation(flashAnim)
        } else {
            overlayView?.findViewById<ImageButton>(R.id.overlay_refresh)?.setOnClickListener {
                serviceScope.launch {
                    if (isManualRefreshInFlight) return@launch
                    isManualRefreshInFlight = true
                    try {
                        updateOnce(showLoading = true, animateRefresh = true)
                    } finally {
                        isManualRefreshInFlight = false
                    }
                }
            }
        }

        // Enable dragging
        overlayView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private suspend fun updateLoop() {
        while (serviceScope.isActive) {
            // Check if silent mode changed
            val currentSilentMode = dataStoreManager.silentQueryMode.first()
            if (currentSilentMode != isSilentMode) {
                isSilentMode = currentSilentMode
                withContext(Dispatchers.Main) {
                    setupOverlay()
                }
            }

            updateOnce(showLoading = false, animateRefresh = false)
            
            val interval = if (isSilentMode) {
                runCatching { dataStoreManager.silentQueryIntervalMs.first() }
                    .getOrDefault(120_000L)
            } else {
                runCatching { dataStoreManager.overlayRefreshIntervalMs.first() }
                    .getOrDefault(DEFAULT_REFRESH_INTERVAL_MS)
            }.coerceIn(5_000L, 10 * 60_000L)
            
            delay(interval)
        }
    }

    private suspend fun updateOnce(showLoading: Boolean, animateRefresh: Boolean) {
        setLoading(isLoading = showLoading, animateRefresh = animateRefresh)

        val state = try {
            val apiKey = dataStoreManager.apiKey.first().orEmpty()
            val selectedJson = dataStoreManager.selectedCarParks.first().orEmpty()
            val selected: List<SelectedCarPark> = if (selectedJson.isNotBlank()) {
                val type = object : TypeToken<List<SelectedCarPark>>() {}.type
                gson.fromJson(selectedJson, type)
            } else {
                emptyList()
            }

            if (apiKey.isBlank() || apiKey == "YOUR_API_KEY_HERE") {
                OverlayDisplayState.Error("Set API Key in app")
            } else if (selected.isEmpty()) {
                OverlayDisplayState.Error("No car parks selected")
            } else {
                val updated = withContext(Dispatchers.IO) {
                    selected.map { carPark ->
                        val details = repository.getCarParkDetailsAndRecord(
                            apiKey = apiKey,
                            facilityId = carPark.id,
                            fallbackName = carPark.name
                        )
                        carPark.copy(availableSpots = details?.availableSpots ?: 0)
                    }
                }
                OverlayDisplayState.Data(updated.take(3))
            }
        } catch (_: Exception) {
            OverlayDisplayState.Error("Update failed")
        }

        render(state)
        setLoading(isLoading = false, animateRefresh = animateRefresh)
    }

    private suspend fun render(state: OverlayDisplayState) {
        val root = overlayView ?: return
        if (isSilentMode) return // No rendering needed for silent mode dot

        val line1 = root.findViewById<TextView>(R.id.overlay_line1)
        val line2 = root.findViewById<TextView>(R.id.overlay_line2)
        val line3 = root.findViewById<TextView>(R.id.overlay_line3)
        val refreshBtn = root.findViewById<ImageButton>(R.id.overlay_refresh)
        val refreshError = root.findViewById<TextView>(R.id.overlay_refresh_error)

        when (state) {
            is OverlayDisplayState.Error -> {
                // Keep previous successful values, just show the error indicator.
                val lines = lastGood.map { "${it.abbr}: ${it.availableSpots}" }
                if (lines.isNotEmpty()) {
                    line1.text = lines.getOrNull(0).orEmpty()
                    line2.text = lines.getOrNull(1).orEmpty()
                    line3.text = lines.getOrNull(2).orEmpty()
                } else {
                    line1.text = state.message
                    line2.text = ""
                    line3.text = ""
                }

                refreshError.visibility = android.view.View.VISIBLE
                refreshBtn.setColorFilter(0xFFFF3B30.toInt())
            }
            is OverlayDisplayState.Data -> {
                val lines = state.carParks.map { "${it.abbr}: ${it.availableSpots}" }
                line1.text = lines.getOrNull(0).orEmpty()
                line2.text = lines.getOrNull(1).orEmpty()
                line3.text = lines.getOrNull(2).orEmpty()

                lastGood = state.carParks
                refreshError.visibility = android.view.View.GONE
                refreshBtn.clearColorFilter()
            }
        }

        applyColors(state)
    }

    private suspend fun applyColors(state: OverlayDisplayState) {
        val root = overlayView ?: return
        if (isSilentMode) return // No colors to apply for silent mode dot

        val line1 = root.findViewById<TextView>(R.id.overlay_line1)
        val line2 = root.findViewById<TextView>(R.id.overlay_line2)
        val line3 = root.findViewById<TextView>(R.id.overlay_line3)

        val carParks = when (state) {
            is OverlayDisplayState.Data -> state.carParks
            is OverlayDisplayState.Error -> lastGood
        }

        val low = runCatching { dataStoreManager.overlayThresholdLow.first() }.getOrDefault(10)
        val mid = runCatching { dataStoreManager.overlayThresholdMid.first() }.getOrDefault(30)
        val red = runCatching { dataStoreManager.overlayColorRed.first() }.getOrDefault(0xFFFF3B30.toInt())
        val orange = runCatching { dataStoreManager.overlayColorOrange.first() }.getOrDefault(0xFFFF9500.toInt())
        val green = runCatching { dataStoreManager.overlayColorGreen.first() }.getOrDefault(0xFF34C759.toInt())

                        fun apply(tv: TextView, spots: Int?) {
            if (spots == null) return
            val (color, isBold) = when {
                spots == 0 -> red to true
                spots <= low -> red to false
                spots <= mid -> orange to false
                else -> green to false
            }
            tv.setTextColor(color)
            val style = if (isBold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            tv.setTypeface(null, style)
        }

        apply(line1, carParks.getOrNull(0)?.availableSpots)
        apply(line2, carParks.getOrNull(1)?.availableSpots)
        apply(line3, carParks.getOrNull(2)?.availableSpots)
    }

    private fun setLoading(isLoading: Boolean, animateRefresh: Boolean) {
        val root = overlayView ?: return
        if (isSilentMode) return // No loading indicator for silent mode dot

        val loading = root.findViewById<ProgressBar>(R.id.overlay_loading)
        val refreshBtn = root.findViewById<ImageButton>(R.id.overlay_refresh)

        loading.visibility = if (isLoading) android.view.View.VISIBLE else android.view.View.GONE

        if (animateRefresh) {
            if (isLoading) {
                val anim: Animation = RotateAnimation(
                    0f,
                    360f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f,
                    Animation.RELATIVE_TO_SELF,
                    0.5f
                ).apply {
                    duration = 650
                    repeatCount = Animation.INFINITE
                    interpolator = LinearInterpolator()
                }
                refreshBtn.startAnimation(anim)
            } else {
                refreshBtn.clearAnimation()
            }
        }
    }

    private fun startForegroundServiceNotification() {
        val channelName = "Parking Overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(chan)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Parking overlay active")
            .setContentText("Tap to open the app")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // ignore
            }
        }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private sealed class OverlayDisplayState {
    data class Error(val message: String) : OverlayDisplayState()
    data class Data(val carParks: List<SelectedCarPark>) : OverlayDisplayState()
}
