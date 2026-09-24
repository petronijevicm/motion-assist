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

package com.rhythmcreative.motionassist

import com.rhythmcreative.motionassist.engine.MotionFilter
import com.rhythmcreative.motionassist.engine.MotionVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MotionEngineTest {

    private val g = 9.81f
    private val stepNs = 10_000_000L // 100 Hz

    /** Simple simulator: feeds gravity, linear acceleration and gyro at 100 Hz. */
    private class Sim(val filter: MotionFilter = MotionFilter()) {
        var ts = 1_000_000_000L
        var maxAbsLateral = 0f
        var maxAbsLongitudinal = 0f
        var maxAbsYaw = 0f

        fun run(
            seconds: Float,
            gravity: (Float) -> FloatArray,
            linear: (Float) -> FloatArray = { floatArrayOf(0f, 0f, 0f) },
            gyro: (Float) -> FloatArray = { floatArrayOf(0f, 0f, 0f) }
        ): MotionVector {
            val steps = (seconds * 100).toInt()
            for (i in 0 until steps) {
                val t = i / 100f
                ts += 10_000_000L
                val gr = gravity(t)
                filter.onGravity(gr[0], gr[1], gr[2])
                val w = gyro(t)
                filter.onGyroscope(w[0], w[1], w[2], ts)
                val a = linear(t)
                filter.onLinearAcceleration(a[0], a[1], a[2], ts)
                val o = filter.output
                maxAbsLateral = maxOf(maxAbsLateral, abs(o.lateral))
                maxAbsLongitudinal = maxOf(maxAbsLongitudinal, abs(o.longitudinal))
                maxAbsYaw = maxOf(maxAbsYaw, abs(o.yawRateRps))
            }
            return filter.output
        }

        fun resetPeaks() {
            maxAbsLateral = 0f
            maxAbsLongitudinal = 0f
            maxAbsYaw = 0f
        }
    }

    private val upright = { _: Float -> floatArrayOf(0f, 9.81f, 0f) }
    private val flat = { _: Float -> floatArrayOf(0f, 0f, 9.81f) }

    @Test
    fun stillPhoneProducesNoMotion() {
        val sim = Sim()
        val out = sim.run(5f, upright)
        assertEquals(0f, out.lateral, 0.001f)
        assertEquals(0f, out.longitudinal, 0.001f)
        assertEquals(0f, out.yawRateRps, 0.001f)
    }

    @Test
    fun brakingWhileHoldingPhoneUprightIsNegativeLongitudinal() {
        val sim = Sim()
        sim.run(1f, upright)
        // Braking at 3 m/s^2: acceleration points backwards, i.e. towards the viewer (+z)
        val out = sim.run(2f, upright, linear = { floatArrayOf(0f, 0f, 3f) })
        assertTrue("longitudinal=${out.longitudinal}", out.longitudinal < -2.5f)
        assertEquals(0f, out.lateral, 0.05f)
    }

    @Test
    fun acceleratingWithPhoneFlatIsPositiveLongitudinal() {
        val sim = Sim()
        sim.run(1f, flat)
        // Flat phone, top edge facing forward: forward = device +y
        val out = sim.run(2f, flat, linear = { floatArrayOf(0f, 2f, 0f) })
        assertTrue("longitudinal=${out.longitudinal}", out.longitudinal > 1.6f)
    }

    @Test
    fun leftTurnGivesNegativeLateralAndPositiveYaw() {
        val sim = Sim()
        sim.run(1f, flat)
        // Centripetal acceleration to the left, rotating counter-clockwise about up
        val out = sim.run(
            3f, flat,
            linear = { floatArrayOf(-2f, 0f, 0f) },
            gyro = { floatArrayOf(0f, 0f, 0.3f) }
        )
        assertTrue("lateral=${out.lateral}", out.lateral < -1.6f)
        assertTrue("yaw=${out.yawRateRps}", out.yawRateRps > 0.2f)
    }

    @Test
    fun yawRateIgnoresPhoneOrientation() {
        // Same vehicle turn, phone held upright: rotation is now about device y
        val sim = Sim()
        sim.run(1f, upright)
        val out = sim.run(2f, upright, gyro = { floatArrayOf(0f, 0.3f, 0f) })
        assertTrue("yaw=${out.yawRateRps}", out.yawRateRps > 0.2f)
    }

    /**
     * Regression: tilting the phone up to look at it (upright -> flat in 0.5 s) used to send
     * the cues flying because a gravity-tilt term was integrated as displacement.
     */
    @Test
    fun lookingUpDoesNotMoveCues() {
        val sim = Sim()
        sim.run(1f, upright)
        sim.resetPeaks()
        val duration = 0.5f
        val rate = (Math.PI / 2 / duration).toFloat()
        sim.run(
            duration,
            gravity = { t ->
                val angle = (t / duration) * (Math.PI / 2)
                floatArrayOf(0f, (g * cos(angle)).toFloat(), (g * sin(angle)).toFloat())
            },
            // Fused linear acceleration glitches during fast rotations
            linear = { t -> floatArrayOf(0f, 2.5f * sin(t * 12f), -2.5f * cos(t * 9f)) },
            gyro = { floatArrayOf(rate, 0f, 0f) }
        )
        sim.run(1f, flat)
        assertTrue("lateral peak=${sim.maxAbsLateral}", sim.maxAbsLateral < 0.3f)
        assertTrue("longitudinal peak=${sim.maxAbsLongitudinal}", sim.maxAbsLongitudinal < 0.3f)
        assertTrue("yaw peak=${sim.maxAbsYaw}", sim.maxAbsYaw < 0.05f)
    }

    @Test
    fun handJitterIsSmoothedOut() {
        val sim = Sim()
        sim.run(1f, upright)
        sim.resetPeaks()
        // 8 Hz tremor / road vibration, 1.5 m/s^2 amplitude
        sim.run(3f, upright, linear = { t -> floatArrayOf(1.5f * sin(t * 50f), 0f, 1.5f * cos(t * 50f)) })
        assertTrue("lateral peak=${sim.maxAbsLateral}", sim.maxAbsLateral < 0.2f)
        assertTrue("longitudinal peak=${sim.maxAbsLongitudinal}", sim.maxAbsLongitudinal < 0.2f)
    }

    @Test
    fun constantSensorOffsetIsRemovedOverTime() {
        val sim = Sim()
        val out = sim.run(120f, flat, linear = { floatArrayOf(0.4f, 0f, 0f) })
        assertEquals(0f, out.lateral, 0.02f)
    }

    @Test
    fun landscapeRotationMapsDeviceYToScreenRight() {
        val filter = MotionFilter()
        filter.setDisplayRotation(1) // ROTATION_90
        val sim = Sim(filter)
        sim.run(1f, flat)
        val out = sim.run(2f, flat, linear = { floatArrayOf(0f, 2f, 0f) })
        assertTrue("lateral=${out.lateral}", out.lateral > 1.6f)
        assertEquals(0f, out.longitudinal, 0.05f)
    }

    @Test
    fun vehicleDetectionTurnsOnWithSustainedAcceleration() {
        val sim = Sim()
        sim.run(2f, upright)
        assertFalse(sim.filter.isVehicleMoving)
        // Stop-and-go traffic: alternating acceleration and braking
        sim.run(12f, upright, linear = { t -> floatArrayOf(0f, 0f, if ((t / 3f).toInt() % 2 == 0) -1.5f else 1.5f) })
        assertTrue(sim.filter.isVehicleMoving)
    }

    @Test
    fun vehicleDetectionIgnoresWalking() {
        val sim = Sim()
        // Walking: strong 2 Hz vertical bounce with some horizontal sway
        sim.run(20f, upright, linear = { t -> floatArrayOf(0.8f * sin(t * 6.3f), 3f * sin(t * 12.6f), 0.6f) })
        assertFalse(sim.filter.isVehicleMoving)
    }

    @Test
    fun uprightPhoneIsLevelWithZeroPitch() {
        val sim = Sim()
        val out = sim.run(1f, upright)
        assertEquals(0f, out.rollRadians, 0.001f)
        assertEquals(0f, out.pitchRadians, 0.001f)
        assertEquals(1f, out.levelConfidence, 0.001f)
    }

    @Test
    fun rollingPhoneClockwiseGivesNegativeRoll() {
        // Top edge tilted 30 degrees to the right: world up now leans towards screen-left
        val a = Math.toRadians(30.0)
        val sim = Sim()
        val out = sim.run(1f, { floatArrayOf((-g * sin(a)).toFloat(), (g * cos(a)).toFloat(), 0f) })
        assertEquals(-Math.toRadians(30.0).toFloat(), out.rollRadians, 0.01f)
    }

    @Test
    fun tiltingScreenUpwardsGivesPositivePitchAndFlatLosesLevel() {
        val a = Math.toRadians(40.0)
        val sim = Sim()
        val tilted = sim.run(1f, { floatArrayOf(0f, (g * cos(a)).toFloat(), (g * sin(a)).toFloat()) })
        assertEquals(Math.toRadians(40.0).toFloat(), tilted.pitchRadians, 0.01f)
        val lying = sim.run(1f, flat)
        assertTrue("confidence=${lying.levelConfidence}", lying.levelConfidence < 0.01f)
    }

    @Test
    fun bumpShowsUpAsVerticalAcceleration() {
        val sim = Sim()
        sim.run(1f, upright)
        // Upward push along world up (device +y when upright)
        val out = sim.run(0.8f, upright, linear = { floatArrayOf(0f, 2f, 0f) })
        assertTrue("vertical=${out.vertical}", out.vertical > 1.2f)
        assertEquals(0f, out.lateral, 0.05f)
        assertEquals(0f, out.longitudinal, 0.05f)
    }

    @Test
    fun gyroBiasIsLearnedWhileAtRest() {
        val sim = Sim()
        // Uncalibrated gyro reads 0.05 rad/s about the vertical axis while lying still
        val biased = { _: Float -> floatArrayOf(0f, 0f, 0.05f) }
        val first = sim.run(0.5f, flat, gyro = biased)
        assertTrue("yaw before learning=${first.yawRateRps}", first.yawRateRps > 0.01f)
        val learned = sim.run(5f, flat, gyro = biased)
        assertEquals(0f, learned.yawRateRps, 0.001f)
    }

    @Test
    fun slowerResponsivenessSmoothsMore() {
        val snappy = Sim(MotionFilter().apply { accelSmoothingSec = 0.1f })
        val calm = Sim(MotionFilter().apply { accelSmoothingSec = 0.6f })
        snappy.run(1f, flat)
        calm.run(1f, flat)
        val s = snappy.run(0.2f, flat, linear = { floatArrayOf(2f, 0f, 0f) })
        val c = calm.run(0.2f, flat, linear = { floatArrayOf(2f, 0f, 0f) })
        assertTrue("snappy=${s.lateral} calm=${c.lateral}", s.lateral > c.lateral * 1.8f)
    }

    @Test
    fun responsivenessMapping() {
        assertEquals(0.6f, MotionFilter.smoothingSecForResponsiveness(0), 0.001f)
        assertEquals(0.25f, MotionFilter.smoothingSecForResponsiveness(70), 0.001f)
        assertEquals(0.1f, MotionFilter.smoothingSecForResponsiveness(100), 0.001f)
    }

    @Test
    fun softDeadband() {
        assertEquals(0f, MotionFilter.softDeadband(0.005f, 0.01f), 0.0001f)
        assertEquals(0f, MotionFilter.softDeadband(-0.005f, 0.01f), 0.0001f)
        assertEquals(0.02f, MotionFilter.softDeadband(0.03f, 0.01f), 0.0001f)
        assertEquals(-0.02f, MotionFilter.softDeadband(-0.03f, 0.01f), 0.0001f)
    }

    @Test
    fun testGoogleToroidalWrapping() {
        val gridWidth = 400f

        fun wrap(coord: Float, limit: Float): Float {
            var v = coord % limit
            if (v < 0) v += limit
            return v
        }

        assertEquals(150f, wrap(150f, gridWidth), 0.001f)
        assertEquals(20f, wrap(420f, gridWidth), 0.001f)
        assertEquals(380f, wrap(-20f, gridWidth), 0.001f)
        assertEquals(380f, wrap(-820f, gridWidth), 0.001f)
    }
}
