/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist.cues

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * An endless, staggered field of cue positions.
 *
 * The field repeats every [periodX] by [periodY] pixels, so it can be shifted by any amount
 * and folded back into view without a visible seam. Rows are staggered by half a column,
 * which is why the row count is always even (an odd count would put two staggered rows next
 * to each other where the pattern repeats).
 *
 * Android-free so the geometry can be unit tested on the JVM.
 */
class CueLattice {

    var periodX = 0f
        private set
    var periodY = 0f
        private set

    private var baseX = FloatArray(0)
    private var baseY = FloatArray(0)

    val count: Int
        get() = baseX.size

    /**
     * Lays out enough cues to cover a [width] x [height] view with the given spacing.
     * [scatter] (0..1) displaces each cue randomly by up to that fraction of the spacing.
     */
    fun layout(width: Float, height: Float, spacingX: Float, spacingY: Float, scatter: Float = 0f, seed: Long = 7L) {
        if (width <= 0f || height <= 0f || spacingX <= 0f || spacingY <= 0f) {
            baseX = FloatArray(0)
            baseY = FloatArray(0)
            periodX = 0f
            periodY = 0f
            return
        }
        val columns = max(1, ceil(width / spacingX).toInt())
        var rows = max(2, ceil(height / spacingY).toInt())
        if (rows % 2 == 1) rows++

        periodX = columns * spacingX
        periodY = rows * spacingY
        // Centre the pattern horizontally so both side bands look the same
        val leftInset = (width - periodX) / 2f
        val random = Random(seed)
        val jitter = scatter.coerceIn(0f, 1f)

        baseX = FloatArray(columns * rows)
        baseY = FloatArray(columns * rows)
        var i = 0
        for (row in 0 until rows) {
            val stagger = if (row % 2 == 1) spacingX / 2f else 0f
            for (column in 0 until columns) {
                val dx = if (jitter > 0f) (random.nextFloat() - 0.5f) * spacingX * jitter else 0f
                val dy = if (jitter > 0f) (random.nextFloat() - 0.5f) * spacingY * jitter else 0f
                baseX[i] = leftInset + column * spacingX + stagger + dx
                baseY[i] = row * spacingY + spacingY / 2f + dy
                i++
            }
        }
    }

    /** X of cue [index] after shifting the field by [shift], folded into 0 until [periodX]. */
    fun x(index: Int, shift: Float): Float = fold(baseX[index] + shift, periodX)

    /** Y of cue [index] after shifting the field by [shift], folded into 0 until [periodY]. */
    fun y(index: Int, shift: Float): Float = fold(baseY[index] + shift, periodY)

    companion object {
        fun fold(value: Float, period: Float): Float {
            if (period <= 0f) return value
            val r = value % period
            return if (r < 0f) r + period else r
        }
    }
}

/**
 * Where on screen cues are allowed, and how big they are at a given point.
 *
 * Cues live in two side bands of [bandFraction] of the width each (0.5 or more fills the
 * screen). They taper to nothing over [taperPx] towards the inner edge of a band and towards
 * the top and bottom of the screen, so they never pop in or out abruptly.
 */
class CueRegion(
    private val width: Float,
    private val height: Float,
    bandFraction: Float,
    private val taperPx: Float
) {
    val fullScreen = bandFraction >= FULL_SCREEN_FRACTION
    val bandWidth = if (fullScreen) width / 2f else width * bandFraction.coerceAtLeast(0f)

    /** 0 = hidden, 1 = full size. Points beyond the left/right screen edges count as band. */
    fun sizeAt(x: Float, y: Float): Float {
        val vertical = taper(min(y, height - y))
        if (fullScreen) return vertical
        val depth = when {
            x <= bandWidth -> bandWidth - x
            x >= width - bandWidth -> x - (width - bandWidth)
            else -> return 0f
        }
        return taper(depth) * vertical
    }

    private fun taper(distance: Float): Float {
        if (taperPx <= 0f) return 1f
        val t = (distance / taperPx).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    companion object {
        const val FULL_SCREEN_FRACTION = 0.5f
    }
}
