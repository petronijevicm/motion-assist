/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist.cues

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import com.petronijevicm.motionassist.R
import kotlin.math.cos
import kotlin.math.sin

/** Outline of a single cue. Stored in settings by [ordinal], so only append new entries. */
enum class CueShape(@StringRes val label: Int) {
    CIRCLE(R.string.shape_circle),
    SQUIRCLE(R.string.shape_squircle),
    PENTAGON(R.string.shape_pentagon),
    DIAMOND(R.string.shape_diamond);

    companion object {
        fun of(ordinal: Int): CueShape = entries.getOrElse(ordinal) { CIRCLE }

        /** Unit pentagon, point up, radius 1. */
        val PENTAGON_X = FloatArray(5) { cos(Math.toRadians(-90.0 + 72.0 * it)).toFloat() }
        val PENTAGON_Y = FloatArray(5) { sin(Math.toRadians(-90.0 + 72.0 * it)).toFloat() }

        /** Corner radius of the squircle relative to its half size. */
        const val SQUIRCLE_CORNER = 0.38f
    }
}

/** Cue colour choice. Stored in settings by [ordinal], so only append new entries. */
enum class CuePalette(@StringRes val label: Int) {
    /** Accent colour of the current Material You theme. */
    SYSTEM(R.string.palette_system),
    SALMON(R.string.palette_salmon),
    AMBER(R.string.palette_amber),
    MINT(R.string.palette_mint),
    SKY(R.string.palette_sky),
    /** Near-white in dark mode, near-black in light mode, for maximum contrast. */
    CONTRAST(R.string.palette_contrast);

    @ColorInt
    fun resolve(context: Context): Int = when (this) {
        SALMON -> Color.rgb(0xF2, 0x9C, 0x86)
        AMBER -> Color.rgb(0xF5, 0xB7, 0x2A)
        MINT -> Color.rgb(0x7C, 0xC9, 0x96)
        SKY -> Color.rgb(0x86, 0xBC, 0xEE)
        CONTRAST -> if (isNight(context)) Color.rgb(0xF4, 0xF4, 0xF4) else Color.rgb(0x1C, 0x1C, 0x1C)
        SYSTEM -> systemAccent(context)
    }

    companion object {
        fun of(ordinal: Int): CuePalette = entries.getOrElse(ordinal) { SYSTEM }

        /** Palettes that random cycling picks from (all except the contrast mode). */
        val CYCLE = listOf(SYSTEM, SALMON, AMBER, MINT, SKY)

        private fun isNight(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

        @ColorInt
        private fun systemAccent(context: Context): Int {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Wallpaper-derived accent; lighter tone on dark backgrounds
                val res = if (isNight(context)) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
                return context.getColor(res)
            }
            return Color.rgb(0x3D, 0x7B, 0xD9)
        }
    }
}
