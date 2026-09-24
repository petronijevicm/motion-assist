/*
 * Copyright (C) 2026 rhythmcreative
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rhythmcreative.motionassist.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.rhythmcreative.motionassist.R
import com.rhythmcreative.motionassist.engine.MotionCuesView
import com.rhythmcreative.motionassist.engine.MotionEstimator
import com.rhythmcreative.motionassist.engine.MotionPreferences
import com.rhythmcreative.motionassist.engine.MotionVector
import com.rhythmcreative.motionassist.ui.MainActivity
import java.util.Random

class MotionAssistService : Service(), MotionEstimator.Callback {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: MotionPreferences
    private lateinit var motionEstimator: MotionEstimator
    private var overlayView: MotionCuesView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isOverlayAttached = false
    private val random = Random()

    private val randomizeRunnable = object : Runnable {
        override fun run() {
            if (prefs.isRandomize && isOverlayAttached) {
                overlayView?.let { view ->
                    view.shapeIndex = random.nextInt(4)
                    view.colorIndex = random.nextInt(5)
                }
                mainHandler.postDelayed(this, 3500L)
            }
        }
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            MotionPreferences.KEY_ENABLED, MotionPreferences.KEY_VEHICLE_AUTO -> updateState()
            MotionPreferences.KEY_COLOR -> overlayView?.colorIndex = prefs.colorIndex
            MotionPreferences.KEY_SHAPE -> overlayView?.shapeIndex = prefs.shapeIndex
            MotionPreferences.KEY_OPACITY -> overlayView?.cueOpacity = prefs.opacity
            MotionPreferences.KEY_RANDOMIZE -> {
                overlayView?.isRandomized = prefs.isRandomize
                if (prefs.isRandomize) {
                    mainHandler.removeCallbacks(randomizeRunnable)
                    mainHandler.post(randomizeRunnable)
                } else {
                    mainHandler.removeCallbacks(randomizeRunnable)
                    overlayView?.shapeIndex = prefs.shapeIndex
                    overlayView?.colorIndex = prefs.colorIndex
                }
            }
            MotionPreferences.KEY_SMOOTH_ANIMATION -> updateFrameRate()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = MotionPreferences(this)
        prefs.registerListener(prefChangeListener)
        motionEstimator = MotionEstimator(this)
        motionEstimator.setCallback(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        updateState()
    }

    private fun updateState() {
        if (!prefs.isEnabled) {
            stopSelf()
            return
        }

        motionEstimator.start()

        val shouldShowOverlay = if (prefs.isVehicleAuto) {
            motionEstimator.isVehicleMoving
        } else {
            true
        }

        if (shouldShowOverlay) {
            attachOverlay()
        } else {
            detachOverlay()
        }
    }

    private fun attachOverlay() {
        if (isOverlayAttached) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            return
        }

        val view = MotionCuesView(this).apply {
            shapeIndex = prefs.shapeIndex
            colorIndex = prefs.colorIndex
            cueOpacity = prefs.opacity
            isRandomized = prefs.isRandomize
            isAdaptiveMode = (prefs.colorIndex == 5)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        windowManager.addView(view, params)
        overlayView = view
        isOverlayAttached = true

        updateFrameRate()

        if (prefs.isRandomize) {
            mainHandler.removeCallbacks(randomizeRunnable)
            mainHandler.post(randomizeRunnable)
        }
    }

    private fun detachOverlay() {
        if (!isOverlayAttached) return
        mainHandler.removeCallbacks(randomizeRunnable)
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
        overlayView = null
        isOverlayAttached = false
    }

    private fun updateFrameRate() {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSave = pm?.isPowerSaveMode == true
        val smooth = prefs.isSmoothAnimation && !isPowerSave

        // Target smooth 120Hz display refresh rate when available
        // overlay view invalidation happens via postInvalidateOnAnimation()
    }

    override fun onMotionUpdated(motion: MotionVector) {
        val dx = motion.x * 20f
        val dy = motion.y * 20f
        mainHandler.post {
            overlayView?.updateOffset(dx, dy)
        }
    }

    override fun onVehicleStateChanged(isMoving: Boolean) {
        mainHandler.post {
            if (prefs.isVehicleAuto) {
                updateState()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterListener(prefChangeListener)
        motionEstimator.stop()
        detachOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_text)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_qs_motion_assist)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "motion_assist_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MotionAssistService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MotionAssistService::class.java))
        }
    }
}
