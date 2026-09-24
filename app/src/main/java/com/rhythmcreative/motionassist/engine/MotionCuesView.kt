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
 * High-performance 2D canvas view for rendering the floating 6-DOF cue field
 * with squircle, pentagon, diamond, circle shapes and adaptive contrast strokes.
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

    // Displacement offset calculated from motion estimator
    private var offsetX = 0f
    private var offsetY = 0f

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

    private fun buildGrid() {
        dots.clear()
        if (width <= 0 || height <= 0) return

        val spacingX = 60f * resources.displayMetrics.density
        val spacingY = 140f * resources.displayMetrics.density
        val radius = 15f * resources.displayMetrics.density

        val colCount = (width / spacingX).toInt() + 2
        val rowCount = (height / spacingY).toInt() + 2

        val totalGridW = colCount * spacingX
        val xOffset = (totalGridW - width) / 2f
        val random = Random(1337L)

        for (row in 0 until rowCount) {
            val rowOffset = if (row % 2 == 1) spacingX / 2f else 0f
            for (col in 0 until colCount) {
                val jitterX = if (isRandomized) ((random.nextFloat() - 0.5f) * spacingX * 0.45f) else 0f
                val jitterY = if (isRandomized) ((random.nextFloat() - 0.5f) * spacingY * 0.45f) else 0f

                dots.add(
                    CueDot(
                        baseX = col * spacingX + rowOffset - xOffset + jitterX,
                        baseY = row * spacingY + jitterY,
                        radius = radius
                    )
                )
            }
        }
    }

    fun updateOffset(dx: Float, dy: Float) {
        offsetX = dx
        offsetY = dy
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

        for (dot in dots) {
            val x = dot.baseX + offsetX
            val y = dot.baseY + offsetY

            // Keep within visible bounds or wrap around smoothly
            if (x < -dot.radius || x > width + dot.radius || y < -dot.radius || y > height + dot.radius) {
                continue
            }

            val r = dot.radius
            val currentFillColor = if (isAdaptiveMode) {
                val alpha = fillPaint.alpha
                if (topDarkIntensity > 0.45f) Color.argb(alpha, 25, 25, 25) else Color.argb(alpha, 245, 245, 245)
            } else {
                fillPaint.color
            }

            fillPaint.color = currentFillColor
            val lum = 0.299 * Color.red(currentFillColor) + 0.587 * Color.green(currentFillColor) + 0.114 * Color.blue(currentFillColor)
            strokePaint.color = if (lum < 135) Color.argb(220, 255, 255, 255) else Color.argb(220, 18, 18, 18)
            strokePaint.strokeWidth = Math.max(2.8f, r * 0.16f)

            when (shapeIndex) {
                1 -> { // Squircle
                    val corner = r * 0.35f
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
                else -> { // Circle (Default)
                    canvas.drawCircle(x, y, r, fillPaint)
                    canvas.drawCircle(x, y, r, strokePaint)
                }
            }
        }
    }
}
