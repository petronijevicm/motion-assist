/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.motionassist

import android.app.motioncues.MotionCuesSettings
import android.app.motioncues.MotionCuesVisualStyle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.PowerManager
import android.os.UserHandle
import android.provider.Settings
import android.view.Choreographer
import com.android.systemui.CoreStartable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.motioncues.MotionCuesUi
import com.android.systemui.plugins.DarkIconDispatcher
import com.android.systemui.settings.UserTracker
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Built-in motion cues for the system: reads the user's Motion Assist settings, runs the
 * on-device sensor fusion and animates the platform [MotionCuesUi] overlay from it.
 *
 * All settings live in Settings.Secure (see [Keys]) and are written by the Settings app.
 */
@SysUISingleton
class MotionAssistController @Inject constructor(
    private val context: Context,
    @Main private val mainHandler: Handler,
    @Main private val mainExecutor: Executor,
    private val cuesUi: MotionCuesUi,
    private val darkIconDispatcher: DarkIconDispatcher,
    private val userTracker: UserTracker,
) : CoreStartable, DarkIconDispatcher.DarkReceiver, MotionEstimator.Callback, UserTracker.Callback {

    private val estimator = MotionEstimator(context).also { it.setCallback(this) }
    private val motion = CueMotion(context.resources.displayMetrics.density)
    private var shownX = 0f
    private var shownY = 0f
    private var lastFrameNanos = 0L
    private var animating = false
    private var gridSignature: String? = null
    private var shuffling = false

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!animating) return
            val dt = if (lastFrameNanos == 0L) 1f / 60f else ((frameTimeNanos - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.05f)
            lastFrameNanos = frameTimeNanos
            motion.step(estimator.latest, dt)
            val dx = motion.offsetX - shownX
            val dy = motion.offsetY - shownY
            if (dx * dx + dy * dy >= MIN_STEP_PX_SQ && cuesUi.isStarted) {
                // MotionCuesUi moves the field by deltas
                cuesUi.updateBubblePos(dx, dy)
                shownX = motion.offsetX
                shownY = motion.offsetY
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val shuffleTick = object : Runnable {
        override fun run() {
            if (!shuffling || !cuesUi.isStarted) return
            applyStyle(PALETTE_CHOICES.random(), (0 until SHAPE_COUNT).random())
            mainHandler.postDelayed(this, SHUFFLE_INTERVAL_MS)
        }
    }

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) = refresh()
    }

    private val systemEvents = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    private var backdropDarkness = 0f

    override fun start() {
        val resolver = context.contentResolver
        for (key in Keys.OBSERVED) {
            resolver.registerContentObserver(Settings.Secure.getUriFor(key), false, settingsObserver, UserHandle.USER_ALL)
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        }
        context.registerReceiver(systemEvents, filter, Context.RECEIVER_NOT_EXPORTED)
        darkIconDispatcher.addDarkReceiver(this)
        userTracker.addCallback(this, mainExecutor)
        refresh()
    }

    override fun onUserChanged(newUser: Int, userContext: Context) = refresh()

    override fun onVehicleStateChanged(isMoving: Boolean) = refresh()

    override fun onDarkChanged(areas: ArrayList<Rect>, darkIntensity: Float, tint: Int) {
        // Dark status bar icons mean the content behind is light
        backdropDarkness = 1f - darkIntensity
        cuesUi.backdropDarkness = backdropDarkness
    }

    /** Brings sensors, overlay and style in line with settings and screen state. */
    private fun refresh() {
        val enabled = readInt(Keys.ENABLED, 0) == 1
        val auto = readInt(Keys.VEHICLE_AUTO, 0) == 1
        val screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive != false

        if (!enabled || (!screenOn && !auto)) {
            hide()
            estimator.stop()
            return
        }

        val show = screenOn && (!auto || estimator.isVehicleMoving)
        estimator.lowPower = !show
        estimator.smoothingSec = MotionFilter.smoothingSecForResponsiveness(readInt(Keys.RESPONSIVENESS, 70))
        estimator.start()
        motion.movementScale = readInt(Keys.MOVEMENT, 100).coerceIn(25, 200) / 100f

        if (show) show() else hide()
    }

    private fun show() {
        val sizePercent = readInt(Keys.SIZE, 100).coerceIn(50, 200)
        val fullScreen = readInt(Keys.MODE, 0) == MODE_FULL_SCREEN
        val signature = "$sizePercent/$fullScreen"
        if (cuesUi.isStarted && signature != gridSignature) cuesUi.stop()

        val saver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        cuesUi.highRefreshRate = readInt(Keys.SMOOTH, 1) == 1 && !saver
        cuesUi.scattered = readInt(Keys.SHUFFLE, 0) == 1
        cuesUi.backdropDarkness = backdropDarkness

        if (!cuesUi.isStarted) {
            val density = context.resources.displayMetrics.density
            val grid = MotionCuesSettings.Builder()
                .setHorizontalSpacingDp(SPACING_X_DP)
                .setVerticalSpacingDp(SPACING_Y_DP)
                // Percent of the width per side; 50 leaves no clear reading area
                .setMarginSizeDp(if (fullScreen) 50 else SIDE_BAND_PERCENT)
                // MotionCuesUi draws this value as pixels
                .setRadiusDp((RADIUS_DP * density * sizePercent / 100f).roundToInt())
                .build()
            cuesUi.start(grid, userTracker.userId, context.packageName)
            gridSignature = signature
            motion.reset()
            shownX = 0f
            shownY = 0f
        }

        shuffling = cuesUi.scattered
        mainHandler.removeCallbacks(shuffleTick)
        if (shuffling) mainHandler.post(shuffleTick) else applyStyle(readInt(Keys.COLOR, 0), readInt(Keys.SHAPE, 0))

        if (!animating) {
            animating = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(frame)
        }
    }

    private fun hide() {
        shuffling = false
        mainHandler.removeCallbacks(shuffleTick)
        if (animating) {
            animating = false
            Choreographer.getInstance().removeFrameCallback(frame)
        }
        if (cuesUi.isStarted) cuesUi.stop()
        gridSignature = null
    }

    private fun applyStyle(colorChoice: Int, shape: Int) {
        val opacity = readInt(Keys.OPACITY, DEFAULT_OPACITY).coerceIn(10, 100)
        val contrast = colorChoice == COLOR_CONTRAST
        val base = when {
            contrast -> Color.WHITE // replaced per frame by MotionCuesUi from the backdrop
            colorChoice in 1 until PALETTE.size -> PALETTE[colorChoice]
            else -> systemAccent()
        }
        val alpha = (opacity * 255 / 100).coerceIn(26, 255)
        cuesUi.contrastMode = contrast
        cuesUi.builtInShape = shape.coerceIn(0, SHAPE_COUNT - 1)
        cuesUi.updateMotionCuesVisualStyle(MotionCuesVisualStyle((alpha shl 24) or (base and 0xFFFFFF), 0))
    }

    private fun systemAccent(): Int {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return context.getColor(
            if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
        )
    }

    private fun readInt(key: String, default: Int): Int =
        Settings.Secure.getIntForUser(context.contentResolver, key, default, userTracker.userId)

    /** Settings.Secure keys shared with the Settings app. */
    object Keys {
        const val ENABLED = "motion_assist_enabled"
        const val VEHICLE_AUTO = "motion_assist_vehicle_auto"
        const val COLOR = "motion_assist_color"
        const val SHAPE = "motion_assist_shape"
        const val OPACITY = "motion_assist_opacity"
        const val SHUFFLE = "motion_assist_randomize"
        const val SMOOTH = "motion_assist_smooth_animation"
        const val MODE = "motion_assist_mode"
        const val MOVEMENT = "motion_assist_movement"
        const val SIZE = "motion_assist_size"
        const val RESPONSIVENESS = "motion_assist_responsiveness"
        const val ADD_TILE = "motion_assist_add_qs_tile"

        val OBSERVED = listOf(
            ENABLED, VEHICLE_AUTO, COLOR, SHAPE, OPACITY, SHUFFLE, SMOOTH, MODE, MOVEMENT, SIZE,
            RESPONSIVENESS, "theme_customization_overlay_packages",
        )
    }

    companion object {
        const val MODE_FULL_SCREEN = 1
        const val COLOR_CONTRAST = 5
        const val SHAPE_COUNT = 4
        const val DEFAULT_OPACITY = 60

        private const val SPACING_X_DP = 46
        private const val SPACING_Y_DP = 52
        private const val RADIUS_DP = 7f
        private const val SIDE_BAND_PERCENT = 20
        private const val MIN_STEP_PX_SQ = 0.0025f
        private const val SHUFFLE_INTERVAL_MS = 3_500L

        /** Index 0 is the wallpaper accent, resolved at runtime. */
        private val PALETTE = intArrayOf(
            0,
            Color.rgb(0xF2, 0x9C, 0x86), // salmon
            Color.rgb(0xF5, 0xB7, 0x2A), // amber
            Color.rgb(0x7C, 0xC9, 0x96), // mint
            Color.rgb(0x86, 0xBC, 0xEE), // sky
        )
        private val PALETTE_CHOICES = PALETTE.indices.toList()
    }
}
