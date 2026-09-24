/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.petronijevicm.motionassist.R
import com.petronijevicm.motionassist.cues.CueFieldView
import com.petronijevicm.motionassist.cues.CuePalette
import com.petronijevicm.motionassist.cues.CueShape
import com.petronijevicm.motionassist.data.CueSettings
import com.petronijevicm.motionassist.engine.MotionEstimator
import com.petronijevicm.motionassist.ui.MainActivity

/**
 * Foreground service that owns the sensors and the full-screen cue overlay.
 *
 * It runs while cues are enabled. In auto-start mode it keeps a low-rate watch on the
 * sensors and only puts the overlay up while the vehicle is moving. Nothing runs while the
 * screen is off (auto-start keeps a batched trickle for detection).
 */
class CueOverlayService : Service(), MotionEstimator.Callback {

    private lateinit var settings: CueSettings
    private lateinit var estimator: MotionEstimator
    private lateinit var windowManager: WindowManager
    private val main = Handler(Looper.getMainLooper())
    private var overlay: CueFieldView? = null

    private val shuffleTick = object : Runnable {
        override fun run() {
            val view = overlay ?: return
            if (!settings.shuffle) return
            view.shape = CueShape.entries.random()
            view.palette = CuePalette.CYCLE.random()
            main.postDelayed(this, SHUFFLE_INTERVAL_MS)
        }
    }

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        val view = overlay
        when (key) {
            CueSettings.Key.ENABLED, CueSettings.Key.AUTO_START -> refresh()
            CueSettings.Key.SHAPE -> if (!settings.shuffle) view?.shape = settings.shape
            CueSettings.Key.PALETTE -> if (!settings.shuffle) view?.palette = settings.palette
            CueSettings.Key.OPACITY -> view?.opacityPercent = settings.opacity
            CueSettings.Key.MOVEMENT -> view?.movementScale = settings.movement / 100f
            CueSettings.Key.SIZE -> view?.sizeScale = settings.size / 100f
            CueSettings.Key.AREA -> view?.bandFraction = settings.area / 100f
            CueSettings.Key.HORIZON -> view?.showHorizon = settings.horizon
            CueSettings.Key.RESPONSIVENESS -> estimator.smoothingSec = settings.smoothingSec
            CueSettings.Key.SMOOTH -> applyFrameRate()
            CueSettings.Key.SHUFFLE -> view?.let { applyShuffle(it) }
        }
    }

    private val systemEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> applyFrameRate()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF -> refresh()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = CueSettings(this)
        estimator = MotionEstimator(this).also { it.setCallback(this) }
        windowManager = getSystemService(WindowManager::class.java)

        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        settings.observe(settingsListener)
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(this, systemEvents, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.enabled = false
            requestTileRefresh(this)
            stopSelf()
            return START_NOT_STICKY
        }
        refresh()
        return START_STICKY
    }

    override fun onDestroy() {
        settings.stopObserving(settingsListener)
        try {
            unregisterReceiver(systemEvents)
        } catch (e: IllegalArgumentException) {
            // never registered
        }
        removeOverlay()
        estimator.stop()
        super.onDestroy()
    }

    override fun onVehicleStateChanged(isMoving: Boolean) {
        if (settings.autoStart) refresh()
    }

    /** Brings sensors and overlay in line with the settings and screen state. */
    private fun refresh() {
        if (!settings.enabled) {
            stopSelf()
            return
        }
        val screenOn = getSystemService(PowerManager::class.java)?.isInteractive != false
        if (!screenOn && !settings.autoStart) {
            removeOverlay()
            estimator.stop()
            return
        }

        val show = screenOn && (!settings.autoStart || estimator.isVehicleMoving)
        estimator.lowPower = !show
        estimator.smoothingSec = settings.smoothingSec
        estimator.start()

        if (show) addOverlay() else removeOverlay()
    }

    private fun addOverlay() {
        if (overlay != null) return
        if (!Settings.canDrawOverlays(this)) return

        val view = CueFieldView(this).apply {
            shape = settings.shape
            palette = settings.palette
            opacityPercent = settings.opacity
            movementScale = settings.movement / 100f
            sizeScale = settings.size / 100f
            bandFraction = settings.area / 100f
            showHorizon = settings.horizon
            motionSource = { estimator.latest }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            title = getString(R.string.app_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Since Android 12 touches only pass through an untrusted overlay whose opacity
                // is at most the system's "maximum obscuring opacity" (0.8 by default).
                // Above that, every app underneath would stop receiving taps.
                val limit = getSystemService(InputManager::class.java)
                    ?.maximumObscuringOpacityForTouch ?: MAX_OBSCURING_OPACITY
                alpha = (limit.coerceAtMost(MAX_OBSCURING_OPACITY) - 0.01f).coerceAtLeast(0f)
            }
        }

        windowManager.addView(view, params)
        overlay = view
        applyFrameRate()
        applyShuffle(view)
    }

    private fun removeOverlay() {
        val view = overlay ?: return
        main.removeCallbacks(shuffleTick)
        view.motionSource = null
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            // already gone
        }
        overlay = null
    }

    private fun applyShuffle(view: CueFieldView) {
        main.removeCallbacks(shuffleTick)
        view.scattered = settings.shuffle
        if (settings.shuffle) {
            main.post(shuffleTick)
        } else {
            view.shape = settings.shape
            view.palette = settings.palette
        }
    }

    private fun applyFrameRate() {
        val saver = getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        overlay?.frameRateCap = if (settings.smooth && !saver) 0 else CAPPED_FPS
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, CueOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "cues"
        private const val NOTIFICATION_ID = 7
        private const val ACTION_STOP = "com.petronijevicm.motionassist.action.STOP"
        private const val SHUFFLE_INTERVAL_MS = 3_500L
        private const val CAPPED_FPS = 30
        private const val MAX_OBSCURING_OPACITY = 0.8f

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, CueOverlayService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CueOverlayService::class.java))
        }

        /** Asks the Quick Settings tile to re-read its state. */
        fun requestTileRefresh(context: Context) {
            try {
                TileService.requestListeningState(context, ComponentName(context, CueTileService::class.java))
            } catch (e: IllegalArgumentException) {
                // tile component not available
            } catch (e: SecurityException) {
                // not allowed from this context
            }
        }
    }
}
