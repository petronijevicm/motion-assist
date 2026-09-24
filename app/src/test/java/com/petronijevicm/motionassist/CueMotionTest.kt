/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist

import com.petronijevicm.motionassist.cues.CueMotion
import com.petronijevicm.motionassist.engine.MotionVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueMotionTest {

    private fun CueMotion.run(seconds: Float, sample: MotionVector, period: Float = 0f) {
        repeat((seconds * 60).toInt()) { step(sample, 1f / 60f, period) }
    }

    @Test
    fun brakingMovesCuesUpAndTheySettleBack() {
        val m = CueMotion(pxPerDp = 1f)
        m.run(1f, MotionVector(lateral = 0f, longitudinal = -3f, yawRateRps = 0f))
        assertEquals(-48f, m.offsetY, 1f) // 16 dp per m/s^2
        m.run(1.5f, MotionVector.ZERO)
        assertEquals(0f, m.offsetY, 0.5f)
    }

    @Test
    fun leftTurnMovesCuesRightAndKeepsScrolling() {
        val m = CueMotion(pxPerDp = 1f)
        m.run(1f, MotionVector(lateral = -2f, longitudinal = 0f, yawRateRps = 0.3f))
        val first = m.offsetX
        assertTrue("offset=$first", first > 32f)
        m.run(1f, MotionVector(lateral = -2f, longitudinal = 0f, yawRateRps = 0.3f))
        // Spring part is steady now, scroll keeps adding 0.3 rad/s * 260 px/rad
        assertEquals(first + 78f, m.offsetX, 2f)
    }

    @Test
    fun displacementIsCapped() {
        val m = CueMotion(pxPerDp = 1f)
        m.run(2f, MotionVector(lateral = 0f, longitudinal = 20f, yawRateRps = 0f))
        assertEquals(72f, m.offsetY, 1f)
    }

    @Test
    fun scrollIsFoldedIntoThePeriod() {
        val m = CueMotion(pxPerDp = 1f)
        m.run(10f, MotionVector(lateral = 0f, longitudinal = 0f, yawRateRps = 1f), period = 100f)
        assertTrue(kotlin.math.abs(m.offsetX) < 100f)
    }

    @Test
    fun movementScaleScalesTheResponse() {
        val m = CueMotion(pxPerDp = 1f).apply { movementScale = 2f }
        m.run(1f, MotionVector(lateral = 0f, longitudinal = 2f, yawRateRps = 0f))
        assertEquals(64f, m.offsetY, 1f)
    }

    @Test
    fun bumpGrowsThenRecovers() {
        val m = CueMotion(pxPerDp = 1f)
        m.run(0.3f, MotionVector(lateral = 0f, longitudinal = 0f, yawRateRps = 0f, vertical = 3f))
        assertEquals(1.21f, m.scale, 0.01f)
        m.run(0.5f, MotionVector.ZERO)
        assertEquals(1f, m.scale, 0.01f)
    }
}
