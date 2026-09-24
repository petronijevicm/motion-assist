/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.motionassist

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns motion samples into how far the cue field should be shifted, in pixels.
 *
 * - Acceleration displaces the field the opposite way, like loose objects in the vehicle:
 *   speeding up moves cues down (towards the viewer), braking moves them up, a left turn moves
 *   them right. A critically damped spring eases towards that target and back to rest.
 * - Turning scrolls the field sideways without limit, like scenery through a side window.
 * - Vertical jolts produce a short-lived size factor ([scale]).
 *
 * Android-free so it can be unit tested and shared with the system (SystemUI) integration.
 */
class CueMotion(private val pxPerDp: Float) {

    /** How far cues travel, 1 = default. */
    var movementScale = 1f

    /** Horizontal shift in pixels (spring + scroll), unbounded; fold it into the cue period. */
    var offsetX = 0f
        private set

    /** Vertical shift in pixels. */
    var offsetY = 0f
        private set

    /** Size factor from vertical jolts, 1 = at rest. */
    var scale = 1f
        private set

    private var springX = 0f
    private var springY = 0f
    private var velocityX = 0f
    private var velocityY = 0f
    private var scroll = 0f

    fun reset() {
        springX = 0f; springY = 0f
        velocityX = 0f; velocityY = 0f
        scroll = 0f
        scale = 1f
        offsetX = 0f; offsetY = 0f
    }

    /**
     * Advances by [dt] seconds. [scrollPeriod] (pixels, 0 = none) keeps the scroll term bounded
     * by folding it into the repeat length of the cue pattern.
     */
    fun step(motion: MotionVector, dt: Float, scrollPeriod: Float = 0f) {
        val gain = PX_PER_MPS2_DP * pxPerDp * movementScale
        var tx = -motion.lateral * gain
        var ty = motion.longitudinal * gain
        val limit = MAX_SHIFT_DP * pxPerDp * max(movementScale, 0.5f)
        val length = sqrt(tx * tx + ty * ty)
        if (length > limit) {
            tx *= limit / length
            ty *= limit / length
        }

        // Critically damped spring, semi-implicit Euler
        val w = SPRING_RATE
        velocityX += (w * w * (tx - springX) - 2f * w * velocityX) * dt
        velocityY += (w * w * (ty - springY) - 2f * w * velocityY) * dt
        springX += velocityX * dt
        springY += velocityY * dt

        // Left turn (positive yaw) scrolls the scenery to the right
        scroll += motion.yawRateRps * SCROLL_DP_PER_RAD * pxPerDp * movementScale * dt
        if (scrollPeriod > 0f) scroll %= scrollPeriod

        val target = 1f + (motion.vertical * BUMP_PER_MPS2 * movementScale).coerceIn(-BUMP_SHRINK, BUMP_GROW)
        scale += (target - scale) * (dt / (BUMP_SMOOTH_SEC + dt))

        offsetX = springX + scroll
        offsetY = springY
    }

    companion object {
        /** Displacement per m/s^2 of vehicle acceleration. */
        const val PX_PER_MPS2_DP = 16f
        /** Largest displacement from acceleration (hard braking is about 5 m/s^2). */
        const val MAX_SHIFT_DP = 72f
        /** Sideways scroll per radian turned. */
        const val SCROLL_DP_PER_RAD = 260f
        /** Spring natural frequency in rad/s; settles in about half a second. */
        const val SPRING_RATE = 9f
        private const val BUMP_PER_MPS2 = 0.07f
        private const val BUMP_GROW = 0.4f
        private const val BUMP_SHRINK = 0.3f
        private const val BUMP_SMOOTH_SEC = 0.06f
    }
}
