/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.motionassist

/**
 * Motion sample in screen space, produced by [MotionFilter].
 *
 * @property lateral horizontal acceleration towards the screen's right edge, m/s^2
 * @property longitudinal horizontal acceleration away from the viewer (vehicle forward), m/s^2
 * @property vertical acceleration along world up (bumps, dips, crests), m/s^2
 * @property yawRateRps rotation about world vertical, positive = turning left, rad/s
 * @property rollRadians angle of world "up" within the screen plane, 0 = screen top is up,
 *   positive when the phone is rolled counter-clockwise
 * @property pitchRadians elevation of the screen normal, 0 = display vertical, +pi/2 = face up
 * @property levelConfidence 0..1, how well defined [rollRadians] is (0 when lying flat)
 * @property isHandling true while the phone itself is being re-oriented in the hand
 */
data class MotionVector(
    val lateral: Float,
    val longitudinal: Float,
    val yawRateRps: Float,
    val vertical: Float = 0f,
    val rollRadians: Float = 0f,
    val pitchRadians: Float = 0f,
    val levelConfidence: Float = 0f,
    val isHandling: Boolean = false
) {
    companion object {
        val ZERO = MotionVector(0f, 0f, 0f)
    }
}
