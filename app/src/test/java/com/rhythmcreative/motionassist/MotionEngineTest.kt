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

import com.rhythmcreative.motionassist.engine.MotionVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MotionEngineTest {

    @Test
    fun testMotionVectorZero() {
        val zero = MotionVector.ZERO
        assertEquals(0f, zero.x, 0.0001f)
        assertEquals(0f, zero.y, 0.0001f)
        assertEquals(0f, zero.outOfPlane, 0.0001f)
        assertEquals(0f, zero.rollRadians, 0.0001f)
        assertEquals(0f, zero.yawRateRps, 0.0001f)
        assertEquals(0f, zero.pitchRateRps, 0.0001f)
    }

    @Test
    fun testMotionVectorCreation() {
        val vector = MotionVector(1.5f, -2.3f, 0.1f, 0.785f, 0.05f, -0.02f)
        assertEquals(1.5f, vector.x, 0.0001f)
        assertEquals(-2.3f, vector.y, 0.0001f)
        assertEquals(0.1f, vector.outOfPlane, 0.0001f)
        assertEquals(0.785f, vector.rollRadians, 0.0001f)
        assertEquals(0.05f, vector.yawRateRps, 0.0001f)
        assertEquals(-0.02f, vector.pitchRateRps, 0.0001f)
    }

    @Test
    fun testDeadbandFormula() {
        fun deadband(v: Float, threshold: Float): Float {
            return when {
                v > threshold -> v - threshold
                v < -threshold -> v + threshold
                else -> 0f
            }
        }

        assertEquals(0f, deadband(0.005f, 0.01f), 0.0001f)
        assertEquals(0f, deadband(-0.005f, 0.01f), 0.0001f)
        assertEquals(0.02f, deadband(0.03f, 0.01f), 0.0001f)
        assertEquals(-0.02f, deadband(-0.03f, 0.01f), 0.0001f)
    }

    @Test
    fun testColorAlphaFormula() {
        fun computeAlpha(opacity: Int): Int {
            return (opacity * 2.55f).toInt().coerceIn(25, 255)
        }

        assertEquals(25, computeAlpha(5))
        assertEquals(153, computeAlpha(60))
        assertEquals(255, computeAlpha(100))
        assertEquals(255, computeAlpha(120))
    }

    @Test
    fun testSpringMassDamperConvergence() {
        // Test harmonic spring-mass-damper 2nd-order ODE integration step
        val springK = 210f
        val dampingC = 22f
        val mass = 1.0f
        val dt = 0.016f // 60 FPS frame time

        var currentX = 50f
        var velocityX = 0f
        val targetX = 0f

        // Run 60 frames (~1.0 second) of physics simulation
        for (frame in 0 until 60) {
            val displacement = currentX - targetX
            val force = -springK * displacement - dampingC * velocityX
            val acceleration = force / mass
            velocityX += acceleration * dt
            currentX += velocityX * dt
        }

        // Particle must converge to equilibrium within tight bound (< 0.05 px)
        assertEquals(0f, currentX, 0.05f)
        assertEquals(0f, velocityX, 0.1f)
    }

    @Test
    fun testCentrifugalForceYawReaction() {
        val yawRate = 0.25f // vehicle turning right at 0.25 rad/s
        val density = 2.0f

        val leftColumnForce = yawRate * 5f * density
        val rightColumnForce = -yawRate * 5f * density

        assertEquals(2.5f, leftColumnForce, 0.001f)
        assertEquals(-2.5f, rightColumnForce, 0.001f)
    }

    @Test
    fun testFluidPhaseLagFactor() {
        val phaseLagTop = 0.0f
        val phaseLagBottom = 1.0f
        val targetX = 20.0f

        val effectiveTop = targetX * (1f - phaseLagTop * 0.22f)
        val effectiveBottom = targetX * (1f - phaseLagBottom * 0.22f)

        assertEquals(20.0f, effectiveTop, 0.001f)
        assertEquals(15.6f, effectiveBottom, 0.001f)
    }
}
