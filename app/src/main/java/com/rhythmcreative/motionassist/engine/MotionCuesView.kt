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
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Official Google Motion Assist peripheral visual cues view with true 2nd-order
 * spring-mass-damper particle physics.
 *
 * Places subtle, elegant kinetic visual dots strictly along the left and right
 * edges of the screen (in the user's peripheral visual field), leaving the entire
 * center area completely unobstructed for reading, typing, and media consumption.
 *
 * Features:
 * - True 2nd-order harmonic spring-mass-damper physics with inertial lag and fluid wave propagation.
 * - Responsive to vehicle acceleration, braking, cornering, device tilt, and interactive touch drag.
 * - Dynamic contrast outer ring for high visibility on both dark and light content.
 * - Material You 3 dynamic color palettes and customizable geometric shapes.
 */
class MotionCuesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val scratchPath = Path()

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

    // Target displacement offsets from external vehicle motion
    private var targetOffsetX = 0f
    private var targetOffsetY = 0f
    private var rollRadians = 0f
    private var yawRateRps = 0f

    // Touch interaction displacement
    private var touchOffsetX = 0f
    private var touchOffsetY = 0f
    private var touchStartX = 0f
    private var touchStartY = 0f

    // High-resolution physics simulation timer
    private var lastFrameTimeNs = 0L

    /**
     * Individual physical cue particle with mass, velocity, spring, and damping.
     */
    data class CueParticle(
        val baseX: Float,
        val baseY: Float,
        val radius: Float,
        val phaseLag: Float,
        val isRightColumn: Boolean,
        var currentX: Float = baseX,
        var currentY: Float = baseY,
        var velocityX: Float = 0f,
        var velocityY: Float = 0f
    ) {
        fun reset() {
            currentX = baseX
            currentY = baseY
            velocityX = 0f
            velocityY = 0f
        }
    }

    private val particles = mutableListOf<CueParticle>()

    init {
        updateColors()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildGrid()
        lastFrameTimeNs = 0L
    }

    /**
     * Builds the official Google Motion Assist peripheral cue columns.
     * Subtle dots are placed along the left and right borders of the screen.
     * The center reading/viewing area is left completely clear.
     */
    private fun buildGrid() {
        particles.clear()
        if (width <= 0 || height <= 0) return

        val density = resources.displayMetrics.density
        // Official Google dot radius: ~6.5dp
        val radius = 6.5f * density
        // Margin from left/right screen edge: 26dp
        val edgeMargin = 26f * density

        // Distribute dots vertically in the peripheral field
        val startY = height * 0.16f
        val endY = height * 0.84f
        val usableHeight = endY - startY

        // 4 dots in compact preview containers, 6 dots in full screen overlay
        val dotCountPerEdge = if (height < 320 * density) 4 else 6
        val stepY = if (dotCountPerEdge > 1) usableHeight / (dotCountPerEdge - 1) else 0f
        val random = Random(1337L)

        for (i in 0 until dotCountPerEdge) {
            val baseY = startY + i * stepY
            val jitterY = if (isRandomized) ((random.nextFloat() - 0.5f) * 14f * density) else 0f
            val jitterX = if (isRandomized) ((random.nextFloat() - 0.5f) * 6f * density) else 0f
            val phaseLag = if (dotCountPerEdge > 1) (i.toFloat() / (dotCountPerEdge - 1)) else 0f

            // Left lateral column
            particles.add(
                CueParticle(
                    baseX = edgeMargin + jitterX,
                    baseY = baseY + jitterY,
                    radius = radius,
                    phaseLag = phaseLag,
                    isRightColumn = false
                )
            )

            // Right lateral column
            particles.add(
                CueParticle(
                    baseX = width - edgeMargin - jitterX,
                    baseY = baseY + jitterY,
                    radius = radius,
                    phaseLag = phaseLag,
                    isRightColumn = true
                )
            )
        }
    }

    /**
     * Smoothly sets the physical displacement vector and rotation from vehicle motion.
     */
    fun updateOffset(dx: Float, dy: Float, rollRad: Float = 0f, yawRate: Float = 0f) {
        val density = resources.displayMetrics.density
        val maxDisplacement = 35f * density
        targetOffsetX = dx.coerceIn(-maxDisplacement, maxDisplacement)
        targetOffsetY = dy.coerceIn(-maxDisplacement, maxDisplacement)
        rollRadians = rollRad
        yawRateRps = yawRate
        postInvalidateOnAnimation()
    }

    /**
     * Applies manual drag displacement to the particle field.
     */
    fun setTouchOffset(dx: Float, dy: Float) {
        val density = resources.displayMetrics.density
        val maxDrag = 42f * density
        touchOffsetX = dx.coerceIn(-maxDrag, maxDrag)
        touchOffsetY = dy.coerceIn(-maxDrag, maxDrag)
        postInvalidateOnAnimation()
    }

    /**
     * Releases touch drag and lets spring-damper recoil snap dots back to equilibrium.
     */
    fun releaseTouch() {
        touchOffsetX = 0f
        touchOffsetY = 0f
        postInvalidateOnAnimation()
    }

    fun resetPhysics() {
        targetOffsetX = 0f
        targetOffsetY = 0f
        touchOffsetX = 0f
        touchOffsetY = 0f
        for (p in particles) {
            p.reset()
        }
        lastFrameTimeNs = 0L
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isTouchInteractive) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - touchStartX) * 0.72f
                val dy = (event.y - touchStartY) * 0.72f
                setTouchOffset(dx, dy)
                onDragListener?.invoke(dx, dy)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                releaseTouch()
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
        if (particles.isEmpty()) return

        val density = resources.displayMetrics.density
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val dt = if (lastFrameTimeNs == 0L) {
            0.016f
        } else {
            ((nowNs - lastFrameTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.033f)
        }
        lastFrameTimeNs = nowNs

        // 2nd-order harmonic spring-mass-damper physics constants
        // Natural frequency: omega_0 = sqrt(k/m) ~ 14.5 rad/s
        // Damping ratio: zeta = c / (2 * sqrt(m*k)) ~ 0.74 (near critical damping with natural organic bounce)
        val springK = 210f
        val dampingC = 22f
        val mass = 1.0f
        val couplingK = 14f

        var hasKineticEnergy = false

        // Update physics for each dot particle independently
        val count = particles.size
        for (i in 0 until count) {
            val p = particles[i]

            // Fluid vertical phase wave: dots follow inertial acceleration with organic delay
            val phaseFactor = p.phaseLag
            val effectiveTargetX = targetOffsetX * (1f - phaseFactor * 0.22f) + touchOffsetX
            val effectiveTargetY = targetOffsetY * (1f - phaseFactor * 0.12f) + touchOffsetY

            // Centrifugal turning displacement from vehicle yaw rate
            val centrifugalForce = if (p.isRightColumn) {
                -yawRateRps * 5f * density
            } else {
                yawRateRps * 5f * density
            }

            val targetX = p.baseX + effectiveTargetX + centrifugalForce
            val targetY = p.baseY + effectiveTargetY

            // Hooke's spring restoring force towards target equilibrium
            val fSpringX = -springK * (p.currentX - targetX)
            val fSpringY = -springK * (p.currentY - targetY)

            // Viscous damping force opposing current velocity
            val fDampX = -dampingC * p.velocityX
            val fDampY = -dampingC * p.velocityY

            // Inter-dot elastic chain coupling along the vertical column
            var fCoupleY = 0f
            // Left column has even indices (0, 2, 4...), Right column has odd indices (1, 3, 5...)
            val prevIndex = i - 2
            val nextIndex = i + 2
            if (prevIndex >= 0) {
                val prev = particles[prevIndex]
                fCoupleY += (prev.currentY - prev.baseY - (p.currentY - p.baseY)) * couplingK
            }
            if (nextIndex < count) {
                val next = particles[nextIndex]
                fCoupleY += (next.currentY - next.baseY - (p.currentY - p.baseY)) * couplingK
            }

            // Semi-implicit Euler integration (stable, robust energy conservation)
            val accelX = (fSpringX + fDampX) / mass
            val accelY = (fSpringY + fDampY + fCoupleY) / mass

            p.velocityX += accelX * dt
            p.velocityY += accelY * dt

            p.currentX += p.velocityX * dt
            p.currentY += p.velocityY * dt

            // Soft elastic boundary protection (dots stay in lateral peripheral zone)
            val maxLatDisp = 38f * density
            p.currentX = p.currentX.coerceIn(p.baseX - maxLatDisp, p.baseX + maxLatDisp)

            // Check if particle still has significant kinetic energy or position error
            if (abs(p.velocityX) > 0.35f || abs(p.velocityY) > 0.35f ||
                abs(p.currentX - targetX) > 0.15f || abs(p.currentY - targetY) > 0.15f
            ) {
                hasKineticEnergy = true
            }
        }

        val strokeWidthPx = 1.35f * density
        strokePaint.strokeWidth = strokeWidthPx

        val baseColor = getResolvedColor()
        val currentFillColor = if (isAdaptiveMode) {
            val alpha = fillPaint.alpha
            if (topDarkIntensity > 0.45f) Color.argb(alpha, 25, 25, 25) else Color.argb(alpha, 245, 245, 245)
        } else {
            val alpha = (cueOpacity * 2.55f).toInt().coerceIn(25, 255)
            (alpha shl 24) or (baseColor and 0x00FFFFFF)
        }

        fillPaint.color = currentFillColor

        // Dynamic high-contrast outline ring (ensures visibility across any app background)
        val lum = 0.299 * Color.red(currentFillColor) + 0.587 * Color.green(currentFillColor) + 0.114 * Color.blue(currentFillColor)
        strokePaint.color = if (lum < 135) Color.argb(210, 255, 255, 255) else Color.argb(200, 20, 20, 20)

        // Render each dot at its physical simulated position
        for (particle in particles) {
            val x = particle.currentX
            val y = particle.currentY
            val r = particle.radius

            when (shapeIndex) {
                1 -> { // Squircle
                    val corner = r * 0.38f
                    canvas.drawRoundRect(x - r, y - r, x + r, y + r, corner, corner, fillPaint)
                    canvas.drawRoundRect(x - r, y - r, x + r, y + r, corner, corner, strokePaint)
                }
                2 -> { // Pentagon
                    scratchPath.rewind()
                    for (i in 0 until 5) {
                        val angle = Math.toRadians((i * 72.0) - 90.0)
                        val px = (x + r * cos(angle)).toFloat()
                        val py = (y + r * sin(angle)).toFloat()
                        if (i == 0) scratchPath.moveTo(px, py) else scratchPath.lineTo(px, py)
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
                else -> { // Circle (Official Google Motion Cues)
                    canvas.drawCircle(x, y, r, fillPaint)
                    canvas.drawCircle(x, y, r, strokePaint)
                }
            }
        }

        if (hasKineticEnergy || targetOffsetX != 0f || targetOffsetY != 0f || touchOffsetX != 0f || touchOffsetY != 0f) {
            postInvalidateOnAnimation()
        }
    }
}
