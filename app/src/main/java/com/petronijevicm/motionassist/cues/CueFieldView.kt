/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist.cues

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
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import com.petronijevicm.motionassist.engine.MotionVector
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * Draws the motion cue field and animates it from a [MotionVector] source once per frame.
 * How the field moves is decided by [CueMotion]; this view adds drawing, touch dragging and
 * the optional horizon line that stays level with the ground.
 */
class CueFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ---- Appearance -------------------------------------------------------------------------

    var shape: CueShape = CueShape.CIRCLE
        set(value) {
            field = value
            invalidate()
        }

    var palette: CuePalette = CuePalette.SYSTEM
        set(value) {
            field = value
            refreshColors()
        }

    /** 0..100 */
    var opacityPercent: Int = 60
        set(value) {
            field = value.coerceIn(0, 100)
            refreshColors()
        }

    /** Cue size multiplier, 1 = default. */
    var sizeScale: Float = 1f
        set(value) {
            field = value.coerceIn(0.4f, 2.5f)
            invalidate()
        }

    /** Side band width as a fraction of the view width; 0.5 or more fills the screen. */
    var bandFraction: Float = 0.2f
        set(value) {
            field = value.coerceIn(0.05f, CueRegion.FULL_SCREEN_FRACTION)
            rebuildRegion()
            invalidate()
        }

    /** Scatter cue positions instead of a regular pattern. */
    var scattered: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            rebuildLattice()
            invalidate()
        }

    var showHorizon: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    // ---- Motion -----------------------------------------------------------------------------

    /** How far cues travel, 1 = default. */
    var movementScale: Float
        get() = motion.movementScale
        set(value) {
            motion.movementScale = value
        }

    /** Sampled once per frame; the frame loop only runs while this is set and the view is shown. */
    var motionSource: (() -> MotionVector)? = null
        set(value) {
            field = value
            syncFrameLoop()
        }

    /** Called every frame with the sample that was used (drives the preview steering wheel). */
    var onFrame: ((MotionVector) -> Unit)? = null

    /** 0 = follow the display, otherwise the most frames per second to draw. */
    var frameRateCap: Int = 0

    /** Lets the user fling the field with a finger (in-app preview). */
    var draggable: Boolean = false

    // ---- State ------------------------------------------------------------------------------

    private val density = resources.displayMetrics.density
    private val lattice = CueLattice()
    private var region = CueRegion(0f, 0f, bandFraction, 0f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val horizonCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val horizonEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    private val motion = CueMotion(density)
    private var dragX = 0f
    private var dragY = 0f

    // What was last drawn
    private var shownX = 0f
    private var shownY = 0f
    private var shownBump = 1f

    // Horizon
    private var horizonRoll = 0f
    private var horizonLift = 0f
    private var horizonBaseline = 0f
    private var horizonOpacity = 0f
    private var horizonPrimed = false

    private var dragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var looping = false
    private var lastFrameNanos = 0L
    private var lastDrawNanos = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!looping) return
            step(frameTimeNanos)
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        refreshColors()
    }

    /** Puts the field back at rest in its home position. */
    fun resetMotion() {
        motion.reset()
        dragX = 0f; dragY = 0f
        shownX = 0f; shownY = 0f; shownBump = 1f
        horizonPrimed = false
        lastFrameNanos = 0L
        invalidate()
    }

    @ColorInt
    fun resolvedColor(): Int = palette.resolve(context)

    // ---- Layout & lifecycle -----------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildLattice()
        rebuildRegion()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshColors() // light/dark switch changes the system and contrast colours
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        syncFrameLoop()
    }

    override fun onDetachedFromWindow() {
        stopLoop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        syncFrameLoop()
    }

    private fun rebuildLattice() {
        lattice.layout(
            width.toFloat(), height.toFloat(),
            SPACING_X_DP * density, SPACING_Y_DP * density,
            scatter = if (scattered) SCATTER else 0f
        )
    }

    private fun rebuildRegion() {
        region = CueRegion(width.toFloat(), height.toFloat(), bandFraction, TAPER_DP * density)
    }

    private fun refreshColors() {
        val base = palette.resolve(context)
        val alpha = (opacityPercent * 255 / 100).coerceIn(MIN_ALPHA, 255)
        fill.color = ColorUtils.setAlphaComponent(base, alpha)
        // Outline in whichever of black/white contrasts with the fill
        val outlineBase = if (ColorUtils.calculateLuminance(base) > 0.45) Color.BLACK else Color.WHITE
        outline.color = ColorUtils.setAlphaComponent(outlineBase, OUTLINE_ALPHA)
        invalidate()
    }

    // ---- Frame loop -------------------------------------------------------------------------

    private fun syncFrameLoop() {
        val run = motionSource != null && isAttachedToWindow && isShown
        if (run && !looping) {
            looping = true
            lastFrameNanos = 0L
            Choreographer.getInstance().postFrameCallback(frameCallback)
        } else if (!run) {
            stopLoop()
        }
    }

    private fun stopLoop() {
        if (!looping) return
        looping = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun step(now: Long) {
        val sample = motionSource?.invoke() ?: MotionVector.ZERO
        val dt = if (lastFrameNanos == 0L) 1f / 60f else ((now - lastFrameNanos) / 1e9f).coerceIn(0.001f, 0.05f)
        lastFrameNanos = now

        motion.step(sample, dt, lattice.periodX)

        if (!dragging) {
            val k = dt / (DRAG_SETTLE_SEC + dt)
            dragX -= dragX * k
            dragY -= dragY * k
        }

        val horizonMoved = showHorizon && stepHorizon(sample, dt)

        onFrame?.invoke(sample)

        if (frameRateCap > 0 && lastDrawNanos != 0L &&
            now - lastDrawNanos < 1_000_000_000L / frameRateCap - FRAME_SLACK_NANOS
        ) {
            return
        }
        val x = motion.offsetX + dragX
        val y = motion.offsetY + dragY
        if (horizonMoved || abs(x - shownX) >= REDRAW_PX || abs(y - shownY) >= REDRAW_PX ||
            abs(motion.scale - shownBump) >= REDRAW_SCALE
        ) {
            shownX = x
            shownY = y
            shownBump = motion.scale
            lastDrawNanos = now
            invalidate()
        }
    }

    /**
     * Roll comes straight from gravity. Pitch is shown relative to a slowly adapting baseline,
     * so the line sits mid-screen at whatever angle the phone is held and moves when the
     * vehicle (or the phone) pitches. Returns true when the line moved visibly.
     */
    private fun stepHorizon(motion: MotionVector, dt: Float): Boolean {
        val roll0 = horizonRoll
        val lift0 = horizonLift
        val opacity0 = horizonOpacity

        if (!horizonPrimed && motion.levelConfidence > 0f) {
            horizonRoll = motion.rollRadians
            horizonBaseline = motion.pitchRadians
            horizonPrimed = true
        }
        val k = dt / (HORIZON_SMOOTH_SEC + dt)
        horizonRoll = wrapAngle(horizonRoll + wrapAngle(motion.rollRadians - horizonRoll) * k)

        val baselineSec = if (motion.isHandling) HORIZON_REBASE_FAST_SEC else HORIZON_REBASE_SEC
        horizonBaseline += (motion.pitchRadians - horizonBaseline) * (dt / (baselineSec + dt))
        val focal = max(width, height) * HORIZON_FOCAL
        val relative = (motion.pitchRadians - horizonBaseline).coerceIn(-0.6f, 0.6f)
        val target = (focal * tan(relative)).coerceIn(-height * 0.4f, height * 0.4f)
        horizonLift += (target - horizonLift) * k

        horizonOpacity += (smoothstep(0.2f, 0.5f, motion.levelConfidence) - horizonOpacity) * k

        return abs(horizonRoll - roll0) > 0.0005f || abs(horizonLift - lift0) > 0.1f ||
            abs(horizonOpacity - opacity0) > 0.005f
    }

    // ---- Touch ------------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!draggable) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                dragX += event.x - lastTouchX
                dragY += event.y - lastTouchY
                lastTouchX = event.x
                lastTouchY = event.y
                if (!looping) invalidateShift()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun invalidateShift() {
        shownX = motion.offsetX + dragX
        shownY = motion.offsetY + dragY
        invalidate()
    }

    // ---- Drawing ----------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val count = lattice.count
        if (count == 0) return

        val baseRadius = RADIUS_DP * density * sizeScale * shownBump
        outline.strokeWidth = max(OUTLINE_MIN_DP * density, baseRadius * OUTLINE_FRACTION)
        val periodX = lattice.periodX
        val viewWidth = width.toFloat()

        for (i in 0 until count) {
            val y = lattice.y(i, shownY)
            val x = lattice.x(i, shownX)
            if (x - baseRadius < viewWidth) drawCue(canvas, x, y, baseRadius)
            // Near the end of the period the same cue also pokes in from the left edge
            if (x > periodX - baseRadius) drawCue(canvas, x - periodX, y, baseRadius)
        }

        if (showHorizon && horizonOpacity > 0.02f) drawHorizon(canvas)
    }

    private fun drawCue(canvas: Canvas, x: Float, y: Float, baseRadius: Float) {
        val r = baseRadius * region.sizeAt(x, y)
        if (r < MIN_VISIBLE_PX) return
        when (shape) {
            CueShape.CIRCLE -> {
                canvas.drawCircle(x, y, r, fill)
                canvas.drawCircle(x, y, r, outline)
            }
            CueShape.SQUIRCLE -> {
                val c = r * CueShape.SQUIRCLE_CORNER
                canvas.drawRoundRect(x - r, y - r, x + r, y + r, c, c, fill)
                canvas.drawRoundRect(x - r, y - r, x + r, y + r, c, c, outline)
            }
            CueShape.PENTAGON -> {
                path.rewind()
                path.moveTo(x + r * CueShape.PENTAGON_X[0], y + r * CueShape.PENTAGON_Y[0])
                for (v in 1 until 5) path.lineTo(x + r * CueShape.PENTAGON_X[v], y + r * CueShape.PENTAGON_Y[v])
                path.close()
                canvas.drawPath(path, fill)
                canvas.drawPath(path, outline)
            }
            CueShape.DIAMOND -> {
                path.rewind()
                path.moveTo(x, y - r)
                path.lineTo(x + r, y)
                path.lineTo(x, y + r)
                path.lineTo(x - r, y)
                path.close()
                canvas.drawPath(path, fill)
                canvas.drawPath(path, outline)
            }
        }
    }

    private fun drawHorizon(canvas: Canvas) {
        // "Up" on screen is (sin roll, -cos roll) in view coordinates; the line is perpendicular
        val ux = sin(horizonRoll)
        val uy = -cos(horizonRoll)
        val cx = width / 2f + ux * horizonLift
        val cy = height / 2f + uy * horizonLift
        val dx = cos(horizonRoll)
        val dy = sin(horizonRoll)
        val reach = (width + height).toFloat()

        val coreAlpha = (max(Color.alpha(fill.color), HORIZON_MIN_ALPHA) * horizonOpacity).toInt()
        horizonCore.color = ColorUtils.setAlphaComponent(fill.color, coreAlpha)
        horizonCore.strokeWidth = HORIZON_WIDTH_DP * density * sizeScale
        horizonEdge.color = ColorUtils.setAlphaComponent(outline.color, (Color.alpha(outline.color) * horizonOpacity).toInt())
        horizonEdge.strokeWidth = horizonCore.strokeWidth + HORIZON_EDGE_DP * density

        // Keep the reading area clear
        val clearFrom = if (region.fullScreen) width * HORIZON_FULL_SCREEN_BAND else region.bandWidth
        canvas.save()
        canvas.clipOutRect(clearFrom, 0f, width - clearFrom, height.toFloat())
        canvas.drawLine(cx - dx * reach, cy - dy * reach, cx + dx * reach, cy + dy * reach, horizonEdge)
        canvas.drawLine(cx - dx * reach, cy - dy * reach, cx + dx * reach, cy + dy * reach, horizonCore)
        canvas.restore()
    }

    companion object {
        // Layout
        private const val SPACING_X_DP = 46f
        private const val SPACING_Y_DP = 52f
        private const val RADIUS_DP = 7f
        private const val TAPER_DP = 28f
        private const val SCATTER = 0.45f
        private const val OUTLINE_MIN_DP = 0.9f
        private const val OUTLINE_FRACTION = 0.16f
        private const val OUTLINE_ALPHA = 215
        private const val MIN_ALPHA = 26
        private const val MIN_VISIBLE_PX = 0.8f

        // Touch
        private const val DRAG_SETTLE_SEC = 0.35f

        // Redraw thresholds
        private const val REDRAW_PX = 0.05f
        private const val REDRAW_SCALE = 0.005f
        private const val FRAME_SLACK_NANOS = 2_000_000L

        // Horizon
        private const val HORIZON_WIDTH_DP = 2.5f
        private const val HORIZON_EDGE_DP = 2.4f
        private const val HORIZON_MIN_ALPHA = 140
        private const val HORIZON_SMOOTH_SEC = 0.08f
        private const val HORIZON_REBASE_SEC = 4f
        private const val HORIZON_REBASE_FAST_SEC = 0.4f
        private const val HORIZON_FOCAL = 0.9f
        private const val HORIZON_FULL_SCREEN_BAND = 0.2f

        private const val PI = 3.1415927f

        private fun wrapAngle(a: Float): Float {
            var r = a
            while (r > PI) r -= 2f * PI
            while (r < -PI) r += 2f * PI
            return r
        }

        private fun smoothstep(from: Float, to: Float, x: Float): Float {
            val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }
    }
}
