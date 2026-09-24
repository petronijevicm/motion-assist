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
}
