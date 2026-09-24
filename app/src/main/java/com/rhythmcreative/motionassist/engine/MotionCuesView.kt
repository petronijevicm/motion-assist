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

package com.rhythmcreative.motionassist.engine

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Official Google Motion Assist / Vehicle Motion Cues drawing and physics engine.
 *
 * Implements Google's exact AOSP toroidal wrapping grid with edge margin filtering,
 * edge shrinking threshold, and a per-frame physics step:
 *  - acceleration displaces the whole field opposite to the vehicle's acceleration, like loose
 *    objects inside the car, and a critically damped spring brings it back when it stops;
 *  - turning (yaw rate) scrolls the field sideways, like scenery through a side window;
 *  - bumps (vertical acceleration) briefly grow or shrink the cues;
 *  - an optional artificial horizon line, levelled with gravity, is drawn in the margins.
 *
 * Motion is pulled from [motionSource] once per display frame, so the sensor rate never
 * floods the UI thread and the animation stays smooth at any refresh rate.
 */
class MotionCuesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.8f
    }
    private val scratchPath = Path()
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val horizonOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    var shapeIndex: Int = 0
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    var colorIndex: Int = 0
        set(value) {
            field = value
            updateColors()
            postInvalidateOnAnimation()
        }

    var cueOpacity: Int = 60
        set(value) {
            field = value
            updateColors()
            postInvalidateOnAnimation()
        }

    var isAdaptiveMode: Boolean = false
        set(value) {
            field = value
            updateColors()
            postInvalidateOnAnimation()
        }

    var isRandomized: Boolean = false
        set(value) {
            field = value
            buildGrid()
            postInvalidateOnAnimation()
        }

    var topDarkIntensity: Float = 0f
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    var isTouchInteractive: Boolean = false
    var onDragListener: ((Float, Float) -> Unit)? = null

    /** Sampled once per frame. When null the render loop is idle. */
    var motionSource: (() -> MotionVector)? = null
        set(value) {
            field = value
            updateFrameLoop()
        }

    /** Invoked every rendered frame with the sample used, for companion UI (preview wheel). */
    var onFrameListener: ((MotionVector) -> Unit)? = null

    /** Multiplier for how far the cues travel (1.0 = default). */
    var sensitivity: Float = 1f

    /** 0 = follow the display refresh rate, otherwise cap the frame rate (power saving). */
    var maxFrameRate: Int = 0

    /** Cue size multiplier (1.0 = default). */
    var sizeScale: Float = 1f
        set(value) {
            field = value.coerceIn(0.4f, 2.5f)
            invalidate()
        }

    /**
     * Width of each side band that shows cues, as a fraction of the view width.
     * 0.5 or more fills the whole screen.
     */
    var cueAreaFraction: Float = DEFAULT_CUE_AREA
        set(value) {
            field = value.coerceIn(0.08f, 0.5f)
            updateMargins()
            invalidate()
        }

    /** Draws an artificial horizon, levelled with gravity, across the side bands. */
    var showHorizon: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // Touch interaction tracking
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    // Physics state (pixels)
    private var springX = 0f
    private var springY = 0f
    private var springVx = 0f
    private var springVy = 0f
    private var flowX = 0f
    private var dragX = 0f
    private var dragY = 0f
    private var drawOffsetX = 0f
    private var drawOffsetY = 0f
    private var lastFrameNs = 0L
    private var lastDrawnFrameNs = 0L
    private var frameLoopRunning = false
    private var bumpScale = 1f

    // Horizon state
    private var horizonRoll = 0f
    private var horizonPitchBaseline = 0f
    private var horizonOffsetPx = 0f
    private var horizonAlpha = 0f
    private var horizonInitialized = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!frameLoopRunning) return
            stepPhysics(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    /**
     * Represents a single motion cue bubble in Google's coordinate space.
     */
    data class MotionCue(
        var x: Float,
        var y: Float,
        var radius: Float
    )

    private val motionCues = mutableListOf<MotionCue>()
    private var bubbleGridWidth = 0f
    private var bubbleGridHeight = 0f
    private var marginLeft = 0f
    private var marginRight = 0f

    // Google Motion Assist official specifications (calibrated to Google Pixel Motion Cues)
    private val horizontalSpacingDp = 48f
    private val verticalSpacingDp = 50f
    private val radiusDp = 6.8f
    private val edgeShrinkThresholdDp = 24f

    init {
        updateColors()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildGrid()
    }

    /**
     * Builds Google's official toroidal staggered grid of bubbles.
     */
    private fun buildGrid() {
        motionCues.clear()
        if (width <= 0 || height <= 0) return

        val density = resources.displayMetrics.density
        val bubbleSpacingX = horizontalSpacingDp * density
        val bubbleSpacingY = verticalSpacingDp * density
        val radius = radiusDp * density

        var rowCount = (height / bubbleSpacingY).toInt() + 1
        val colCount = (width / bubbleSpacingX).toInt() + 1
        if (rowCount % 2 != 0) {
            rowCount++
        }

        bubbleGridWidth = colCount * bubbleSpacingX
        bubbleGridHeight = rowCount * bubbleSpacingY
        val xOffset = (bubbleGridWidth - width) / 2f
        val random = Random(1337L)

        for (row in 0 until rowCount) {
            for (col in 0 until colCount) {
                val rowOffset = if (row % 2 == 1) bubbleSpacingX / 2f else 0f
                val jitterX = if (isRandomized) ((random.nextFloat() - 0.5f) * bubbleSpacingX * 0.45f) else 0f
                val jitterY = if (isRandomized) ((random.nextFloat() - 0.5f) * bubbleSpacingY * 0.45f) else 0f

                motionCues.add(
                    MotionCue(
                        x = col * bubbleSpacingX + rowOffset - xOffset + jitterX,
                        y = row * bubbleSpacingY + jitterY,
                        radius = radius
                    )
                )
            }
        }

        updateMargins()
    }

    private fun updateMargins() {
        if (cueAreaFraction >= 0.5f) {
            // Full screen: no clear reading zone
            marginLeft = width.toFloat()
            marginRight = width.toFloat()
        } else {
            marginLeft = width * cueAreaFraction
            marginRight = width - marginLeft
        }
    }

    private val isFullScreenCues: Boolean
        get() = cueAreaFraction >= 0.5f

    /**
     * Shifts the whole field by a displacement delta (manual / legacy use).
     */
    fun updateBubblePos(dx: Float, dy: Float) {
        dragX += dx
        dragY += dy
        refreshOffset()
    }

    fun resetPhysics() {
        springX = 0f
        springY = 0f
        springVx = 0f
        springVy = 0f
        flowX = 0f
        dragX = 0f
        dragY = 0f
        lastFrameNs = 0L
        drawOffsetX = 0f
        drawOffsetY = 0f
        bumpScale = 1f
        horizonInitialized = false
        buildGrid()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateFrameLoop()
    }

    override fun onDetachedFromWindow() {
        stopFrameLoop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        updateFrameLoop()
    }

    private fun updateFrameLoop() {
        val shouldRun = motionSource != null && isAttachedToWindow && isShown
        if (shouldRun && !frameLoopRunning) {
            frameLoopRunning = true
            lastFrameNs = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else if (!shouldRun) {
            stopFrameLoop()
        }
    }

    private fun stopFrameLoop() {
        if (!frameLoopRunning) return
        frameLoopRunning = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun stepPhysics(frameTimeNanos: Long) {
        val motion = motionSource?.invoke() ?: MotionVector.ZERO
        val dt = if (lastFrameNs == 0L) 1f / 60f else ((frameTimeNanos - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNs = frameTimeNanos

        val density = resources.displayMetrics.density
        val gain = ACCEL_GAIN_DP * density * sensitivity
        val maxOffset = MAX_ACCEL_OFFSET_DP * density * sensitivity.coerceAtLeast(0.5f)

        // Cues react like free objects in the vehicle: opposite to its acceleration.
        // Speeding up pushes them back (down), braking pushes them forward (up),
        // a left turn pushes them right.
        var targetX = -motion.lateral * gain
        var targetY = motion.longitudinal * gain
        val targetLen = sqrt(targetX * targetX + targetY * targetY)
        if (targetLen > maxOffset) {
            val k = maxOffset / targetLen
            targetX *= k
            targetY *= k
        }

        // Critically damped spring towards the target (semi-implicit Euler)
        val w = SPRING_OMEGA
        springVx += (w * w * (targetX - springX) - 2f * w * springVx) * dt
        springVy += (w * w * (targetY - springY) - 2f * w * springVy) * dt
        springX += springVx * dt
        springY += springVy * dt

        // Turning scrolls the field sideways (left turn -> scenery moves right)
        flowX += motion.yawRateRps * YAW_FLOW_DP_PER_RAD * density * sensitivity * dt
        if (bubbleGridWidth > 0f) flowX %= bubbleGridWidth

        // Manual drag offset springs back once released
        if (!isDragging) {
            val decay = dt / (DRAG_RETURN_SEC + dt)
            dragX -= dragX * decay
            dragY -= dragY * decay
        }

        // Bumps: upward push briefly enlarges the cues, a dip shrinks them
        val bumpTarget = 1f + (motion.vertical * BUMP_GAIN * sensitivity).coerceIn(-BUMP_MAX_SHRINK, BUMP_MAX_GROW)
        bumpScale += (bumpTarget - bumpScale) * (dt / (BUMP_SMOOTHING_SEC + dt))

        val horizonChanged = showHorizon && stepHorizon(motion, dt)

        onFrameListener?.invoke(motion)

        if (maxFrameRate > 0 && lastDrawnFrameNs != 0L &&
            frameTimeNanos - lastDrawnFrameNs < 1_000_000_000L / maxFrameRate - 2_000_000L) {
            return
        }
        refreshOffset(frameTimeNanos, horizonChanged || abs(bumpScale - drawnBumpScale) > 0.005f)
    }

    /**
     * Levels the horizon with gravity (roll) and moves it with pitch changes relative to a
     * slowly adapting baseline, so it stays near the middle for any comfortable holding angle
     * but reacts to the vehicle (or phone) pitching. Returns true when a redraw is needed.
     */
    private fun stepHorizon(motion: MotionVector, dt: Float): Boolean {
        val oldRoll = horizonRoll
        val oldOffset = horizonOffsetPx
        val oldAlpha = horizonAlpha

        if (!horizonInitialized && motion.levelConfidence > 0f) {
            horizonRoll = motion.rollRadians
            horizonPitchBaseline = motion.pitchRadians
            horizonInitialized = true
        }

        val a = dt / (HORIZON_SMOOTHING_SEC + dt)
        horizonRoll += wrapAngle(motion.rollRadians - horizonRoll) * a
        horizonRoll = wrapAngle(horizonRoll)

        // Re-centre quickly while the user is re-orienting the phone, slowly otherwise
        val baselineSec = if (motion.isHandling) HORIZON_BASELINE_FAST_SEC else HORIZON_BASELINE_SEC
        horizonPitchBaseline += (motion.pitchRadians - horizonPitchBaseline) * (dt / (baselineSec + dt))
        val focal = max(width, height) * HORIZON_FOCAL_FRACTION
        val relPitch = (motion.pitchRadians - horizonPitchBaseline).coerceIn(-0.6f, 0.6f)
        val targetOffset = (focal * tan(relPitch)).coerceIn(-height * 0.4f, height * 0.4f)
        horizonOffsetPx += (targetOffset - horizonOffsetPx) * a

        // Fade out when the phone lies flat and "level" becomes meaningless
        val targetAlpha = smoothstep(0.2f, 0.5f, motion.levelConfidence)
        horizonAlpha += (targetAlpha - horizonAlpha) * a

        return abs(horizonRoll - oldRoll) > 0.0005f ||
            abs(horizonOffsetPx - oldOffset) > 0.1f ||
            abs(horizonAlpha - oldAlpha) > 0.005f
    }

    private var drawnBumpScale = 1f

    private fun refreshOffset(frameTimeNanos: Long = 0L, force: Boolean = false) {
        val newX = springX + flowX + dragX
        val newY = springY + dragY
        if (!force && abs(newX - drawOffsetX) < 0.05f && abs(newY - drawOffsetY) < 0.05f) return
        drawOffsetX = newX
        drawOffsetY = newY
        drawnBumpScale = bumpScale
        if (frameTimeNanos != 0L) lastDrawnFrameNs = frameTimeNanos
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isTouchInteractive) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                updateBubblePos(dx, dy)
                onDragListener?.invoke(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                onDragListener?.invoke(0f, 0f)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun getResolvedColor(): Int {
        return when (colorIndex) {
            1 -> Color.rgb(0xFF, 0xAB, 0x91) // Salmon pink
            2 -> Color.rgb(0xFF, 0xC1, 0x07) // Amber yellow
            3 -> Color.rgb(0x81, 0xC7, 0x84) // Mint green
            4 -> Color.rgb(0x90, 0xCA, 0xF9) // Soft blue
            5 -> {
                // Adaptive contrast: black on light background, white on dark background
                val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                if (isDark) Color.rgb(0xF5, 0xF5, 0xF5) else Color.rgb(0x19, 0x19, 0x19)
            }
            else -> {
                // System default dynamic Material You accent
                try {
                    MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.rgb(0x1A, 0x73, 0xE8))
                } catch (e: Exception) {
                    Color.rgb(0x1A, 0x73, 0xE8)
                }
            }
        }
    }

    private fun updateColors() {
        val baseColor = getResolvedColor()
        val alpha = (cueOpacity * 2.55f).toInt().coerceIn(25, 255)
        fillPaint.color = (alpha shl 24) or (baseColor and 0x00FFFFFF)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (motionCues.isEmpty() || bubbleGridWidth <= 0f || bubbleGridHeight <= 0f) return

        val density = resources.displayMetrics.density
        val edgeShrinkThreshold = edgeShrinkThresholdDp * density
        val currentFillColor = if (isAdaptiveMode) {
            val alpha = fillPaint.alpha
            if (topDarkIntensity > 0.45f) Color.argb(alpha, 25, 25, 25) else Color.argb(alpha, 245, 245, 245)
        } else {
            fillPaint.color
        }

        val lum = 0.299 * Color.red(currentFillColor) + 0.587 * Color.green(currentFillColor) + 0.114 * Color.blue(currentFillColor)
        val strokeAlpha = 220
        strokePaint.color = if (lum < 135) {
            Color.argb(strokeAlpha, 255, 255, 255)
        } else {
            Color.argb(strokeAlpha, 18, 18, 18)
        }

        val sizeFactor = sizeScale * drawnBumpScale
        val fullScreen = isFullScreenCues

        for (motionCue in motionCues) {
            // Google Toroidal Wrap: wrap around screen dimensions seamlessly
            var x = (motionCue.x + drawOffsetX) % bubbleGridWidth
            var y = (motionCue.y + drawOffsetY) % bubbleGridHeight

            if (x < 0) x += bubbleGridWidth
            if (y < 0) y += bubbleGridHeight

            // Google Margin Rule: Only draw in the peripheral margins, leaving the center clear
            if (!fullScreen && x > marginLeft && x < marginRight) {
                continue
            }

            // Google Edge Shrink Rule: Calculate distance from nearest boundary
            val distanceFromEdgeY = min(y, height.toFloat() - y)
            val distanceFromEdge = if (fullScreen) {
                distanceFromEdgeY
            } else {
                min(min(abs(marginRight - x), abs(marginLeft - x)), distanceFromEdgeY)
            }

            // Adjust radius based on distance from edge
            var adjustedRadius = motionCue.radius * sizeFactor
            if (distanceFromEdge < edgeShrinkThreshold) {
                val shrinkFactor = distanceFromEdge / edgeShrinkThreshold
                adjustedRadius *= shrinkFactor
            }

            if (adjustedRadius <= 0.8f) continue

            val r = adjustedRadius
            strokePaint.strokeWidth = Math.max(2.4f, r * 0.16f)

            when (shapeIndex) {
                1 -> { // Squircle
                    val corner = r * 0.35f
                    canvas.drawRoundRect(x - r, y - r, x + r, y + r, corner, corner, fillPaint)
                    canvas.drawRoundRect(x - r, y - r, x + r, y + r, corner, corner, strokePaint)
                }
                2 -> { // Pentagon
                    scratchPath.rewind()
                    scratchPath.moveTo(x + r * PENTAGON_X[0], y + r * PENTAGON_Y[0])
                    for (i in 1 until 5) {
                        scratchPath.lineTo(x + r * PENTAGON_X[i], y + r * PENTAGON_Y[i])
                    }
                    scratchPath.close()
                    canvas.drawPath(scratchPath, fillPaint)
                    canvas.drawPath(scratchPath, strokePaint)
                }
                3 -> { // Diamond
                    scratchPath.rewind()
                    scratchPath.moveTo(x, y - r)
                    scratchPath.lineTo(x + r, y)
                    scratchPath.lineTo(x, y + r)
                    scratchPath.lineTo(x - r, y)
                    scratchPath.close()
                    canvas.drawPath(scratchPath, fillPaint)
                    canvas.drawPath(scratchPath, strokePaint)
                }
                else -> { // Circle (Official Google Default)
                    canvas.drawCircle(x, y, r, fillPaint)
                    canvas.drawCircle(x, y, r, strokePaint)
                }
            }
        }

        if (showHorizon && horizonAlpha > 0.02f) {
            drawHorizon(canvas, currentFillColor)
        }
    }

    private fun drawHorizon(canvas: Canvas, color: Int) {
        val density = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        // Screen "up" in view coordinates is (sin roll, -cos roll); the line runs perpendicular
        val dirX = cos(horizonRoll)
        val dirY = sin(horizonRoll)
        val centerX = cx + sin(horizonRoll) * horizonOffsetPx
        val centerY = cy - cos(horizonRoll) * horizonOffsetPx
        val halfLen = width.toFloat() + height.toFloat()

        val alphaScale = horizonAlpha
        val coreAlpha = (Color.alpha(color).coerceAtLeast(140) * alphaScale).toInt()
        horizonPaint.color = Color.argb(coreAlpha, Color.red(color), Color.green(color), Color.blue(color))
        horizonPaint.strokeWidth = HORIZON_WIDTH_DP * density * sizeScale
        horizonOutlinePaint.color = Color.argb((Color.alpha(strokePaint.color) * alphaScale).toInt(),
            Color.red(strokePaint.color), Color.green(strokePaint.color), Color.blue(strokePaint.color))
        horizonOutlinePaint.strokeWidth = horizonPaint.strokeWidth + 2.4f * density

        // Keep the reading zone clear: only draw where cues are drawn (or the outer 20%)
        val gapLeft = if (isFullScreenCues) width * HORIZON_FULLSCREEN_BAND else marginLeft
        val gapRight = width - gapLeft
        canvas.save()
        canvas.clipOutRect(gapLeft, 0f, gapRight, height.toFloat())
        val x0 = centerX - dirX * halfLen
        val y0 = centerY - dirY * halfLen
        val x1 = centerX + dirX * halfLen
        val y1 = centerY + dirY * halfLen
        canvas.drawLine(x0, y0, x1, y1, horizonOutlinePaint)
        canvas.drawLine(x0, y0, x1, y1, horizonPaint)
        canvas.restore()
    }

    companion object {
        /** Cue displacement per m/s^2 of vehicle acceleration. */
        const val ACCEL_GAIN_DP = 16f
        /** Largest displacement caused by acceleration (hard braking ~ 5 m/s^2). */
        const val MAX_ACCEL_OFFSET_DP = 72f
        /** Sideways scroll per radian of turn. */
        const val YAW_FLOW_DP_PER_RAD = 260f
        /** Spring natural frequency (rad/s); critically damped, settles in ~0.5 s. */
        const val SPRING_OMEGA = 9f
        private const val DRAG_RETURN_SEC = 0.35f

        const val DEFAULT_CUE_AREA = 0.20f

        /** Relative size change per m/s^2 of vertical acceleration. */
        private const val BUMP_GAIN = 0.07f
        private const val BUMP_MAX_GROW = 0.4f
        private const val BUMP_MAX_SHRINK = 0.3f
        private const val BUMP_SMOOTHING_SEC = 0.06f

        private const val HORIZON_WIDTH_DP = 2.5f
        private const val HORIZON_SMOOTHING_SEC = 0.08f
        private const val HORIZON_BASELINE_SEC = 4f
        private const val HORIZON_BASELINE_FAST_SEC = 0.4f
        /** Virtual camera focal length as a fraction of the longer screen side. */
        private const val HORIZON_FOCAL_FRACTION = 0.9f
        private const val HORIZON_FULLSCREEN_BAND = 0.2f

        private val PENTAGON_X = FloatArray(5) { cos(Math.toRadians(it * 72.0 - 90.0)).toFloat() }
        private val PENTAGON_Y = FloatArray(5) { sin(Math.toRadians(it * 72.0 - 90.0)).toFloat() }

        private fun wrapAngle(a: Float): Float {
            var r = a
            while (r > PI_F) r -= 2f * PI_F
            while (r < -PI_F) r += 2f * PI_F
            return r
        }

        private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        private const val PI_F = 3.1415927f
    }
}
