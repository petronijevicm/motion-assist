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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Official Google Motion Assist peripheral visual cues view.
 *
 * Places subtle, elegant kinetic visual dots strictly along the left and right
 * edges of the screen (in the user's peripheral visual field), leaving the entire
 * center area completely unobstructed for reading, typing, and media consumption.
 *
 * Features:
 * - Fluid spring-damper physical inertia responding to vehicle acceleration/braking/turning.
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

    // Physics spring-damper displacement offsets
    private var targetOffsetX = 0f
    private var targetOffsetY = 0f
    private var currentOffsetX = 0f
    private var currentOffsetY = 0f

    data class CueDot(
        val baseX: Float,
        val baseY: Float,
        val radius: Float
    )

    private val dots = mutableListOf<CueDot>()

    init {
        updateColors()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildGrid()
    }

    /**
     * Builds the official Google Motion Assist peripheral cue columns.
     * Subtle dots are placed along the left and right borders of the screen.
     * The center reading/viewing area is left completely clear.
     */
    private fun buildGrid() {
        dots.clear()
        if (width <= 0 || height <= 0) return

        val density = resources.displayMetrics.density
        // Official Google dot radius: subtle ~6.5dp
        val radius = 6.5f * density
        // Margin from left/right screen edge: 26dp
        val edgeMargin = 26f * density

        // Distribute dots vertically in the peripheral field
        // From ~15% screen height to ~85% screen height (clearing status bar and nav bar)
        val startY = height * 0.16f
        val endY = height * 0.84f
        val usableHeight = endY - startY

        // 6 subtle peripheral dots along each lateral edge
        val dotCountPerEdge = 6
        val stepY = usableHeight / (dotCountPerEdge - 1)
        val random = Random(1337L)

        for (i in 0 until dotCountPerEdge) {
            val baseY = startY + i * stepY
            val jitterY = if (isRandomized) ((random.nextFloat() - 0.5f) * 14f * density) else 0f
            val jitterX = if (isRandomized) ((random.nextFloat() - 0.5f) * 6f * density) else 0f

            // Left lateral column
            dots.add(
                CueDot(
                    baseX = edgeMargin + jitterX,
                    baseY = baseY + jitterY,
                    radius = radius
                )
            )

            // Right lateral column
            dots.add(
                CueDot(
                    baseX = width - edgeMargin - jitterX,
                    baseY = baseY + jitterY,
                    radius = radius
                )
            )
        }
    }

    /**
     * Smoothly sets the physical displacement vector from vehicle motion.
     */
    fun updateOffset(dx: Float, dy: Float) {
        val density = resources.displayMetrics.density
        val maxDisplacement = 28f * density
        targetOffsetX = dx.coerceIn(-maxDisplacement, maxDisplacement)
        targetOffsetY = dy.coerceIn(-maxDisplacement, maxDisplacement)
        postInvalidateOnAnimation()
    }

    private fun updateColors() {
        val baseColor = when (colorIndex) {
            1 -> Color.rgb(0xFF, 0xAB, 0x91) // Salmon pink
            2 -> Color.rgb(0xFF, 0xC1, 0x07) // Amber yellow
            3 -> Color.rgb(0x81, 0xC7, 0x84) // Mint green
            4 -> Color.rgb(0x90, 0xCA, 0xF9) // Soft blue
            5 -> Color.rgb(0xBA, 0x68, 0xC8) // Adaptive placeholder
            else -> {
                // System default dynamic Material You accent
                try {
                    MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, Color.rgb(0x1A, 0x73, 0xE8))
                } catch (e: Exception) {
                    Color.rgb(0x1A, 0x73, 0xE8)
                }
            }
        }

        val alpha = (cueOpacity * 2.55f).toInt().coerceIn(25, 255)
        fillPaint.color = (alpha shl 24) or (baseColor and 0x00FFFFFF)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (dots.isEmpty()) return

        val density = resources.displayMetrics.density

        // Fluid spring-damper physical interpolation (60Hz / 120Hz smooth float)
        val damping = 0.20f
        currentOffsetX += (targetOffsetX - currentOffsetX) * damping
        currentOffsetY += (targetOffsetY - currentOffsetY) * damping

        val keepAnimating = Math.abs(targetOffsetX - currentOffsetX) > 0.08f ||
                Math.abs(targetOffsetY - currentOffsetY) > 0.08f

        val strokeWidthPx = 1.35f * density
        strokePaint.strokeWidth = strokeWidthPx

        for (dot in dots) {
            // Slight organic vertical phase wave for realistic liquid fluid inertia
            val phaseFactor = (dot.baseY / height) * 0.12f
            val x = dot.baseX + currentOffsetX * (1f - phaseFactor)
            val y = dot.baseY + currentOffsetY

            val r = dot.radius
            val currentFillColor = if (isAdaptiveMode) {
                val alpha = fillPaint.alpha
                if (topDarkIntensity > 0.45f) Color.argb(alpha, 25, 25, 25) else Color.argb(alpha, 245, 245, 245)
            } else {
                fillPaint.color
            }

            fillPaint.color = currentFillColor

            // Dynamic high-contrast outline ring (ensures visibility across any app background)
            val lum = 0.299 * Color.red(currentFillColor) + 0.587 * Color.green(currentFillColor) + 0.114 * Color.blue(currentFillColor)
            strokePaint.color = if (lum < 135) Color.argb(210, 255, 255, 255) else Color.argb(200, 20, 20, 20)

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

        if (keepAnimating) {
            postInvalidateOnAnimation()
        }
    }
}
