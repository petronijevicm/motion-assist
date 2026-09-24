/*
 * SPDX-FileCopyrightText: 2026 petronijevicm
 * SPDX-License-Identifier: Apache-2.0
 */

package com.petronijevicm.motionassist

import com.petronijevicm.motionassist.cues.CueLattice
import com.petronijevicm.motionassist.cues.CueRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueLatticeTest {

    @Test
    fun foldKeepsValuesInsideThePeriod() {
        assertEquals(150f, CueLattice.fold(150f, 400f), 0.001f)
        assertEquals(20f, CueLattice.fold(420f, 400f), 0.001f)
        assertEquals(380f, CueLattice.fold(-20f, 400f), 0.001f)
        assertEquals(380f, CueLattice.fold(-820f, 400f), 0.001f)
    }

    @Test
    fun latticeCoversTheViewWithAnEvenRowCount() {
        val lattice = CueLattice()
        lattice.layout(1080f, 2400f, 120f, 130f)
        assertTrue(lattice.periodX >= 1080f)
        assertTrue(lattice.periodY >= 2400f)
        val rows = Math.round(lattice.periodY / 130f)
        assertEquals(0, rows % 2)
        assertEquals(Math.round(lattice.periodX / 120f) * rows, lattice.count)
    }

    @Test
    fun shiftingByOnePeriodChangesNothing() {
        val lattice = CueLattice()
        lattice.layout(1000f, 1800f, 110f, 120f, scatter = 0.4f)
        for (i in 0 until lattice.count) {
            assertEquals(lattice.x(i, 37f), lattice.x(i, 37f + lattice.periodX), 0.01f)
            assertEquals(lattice.y(i, -52f), lattice.y(i, -52f - lattice.periodY), 0.01f)
        }
    }

    @Test
    fun scatterIsRepeatable() {
        val a = CueLattice().apply { layout(900f, 1600f, 100f, 100f, scatter = 0.45f, seed = 3L) }
        val b = CueLattice().apply { layout(900f, 1600f, 100f, 100f, scatter = 0.45f, seed = 3L) }
        for (i in 0 until a.count) assertEquals(a.x(i, 0f), b.x(i, 0f), 0f)
    }

    @Test
    fun emptyViewHasNoCues() {
        val lattice = CueLattice()
        lattice.layout(0f, 0f, 100f, 100f)
        assertEquals(0, lattice.count)
    }

    @Test
    fun readingAreaStaysClear() {
        val region = CueRegion(1000f, 2000f, bandFraction = 0.2f, taperPx = 50f)
        assertEquals(0f, region.sizeAt(500f, 1000f), 0f)
        assertEquals(1f, region.sizeAt(20f, 1000f), 0.001f)
        assertEquals(1f, region.sizeAt(980f, 1000f), 0.001f)
        // Tapers towards the inner edge of a band
        val nearInner = region.sizeAt(190f, 1000f)
        assertTrue(nearInner > 0f && nearInner < 0.2f)
    }

    @Test
    fun cuesTaperAtTopAndBottom() {
        val region = CueRegion(1000f, 2000f, bandFraction = 0.2f, taperPx = 50f)
        assertEquals(0f, region.sizeAt(20f, 0f), 0.001f)
        assertEquals(0f, region.sizeAt(20f, 2000f), 0.001f)
        assertEquals(0.5f, region.sizeAt(20f, 25f), 0.001f)
    }

    @Test
    fun halfWidthBandsFillTheScreen() {
        val region = CueRegion(1000f, 2000f, bandFraction = 0.5f, taperPx = 50f)
        assertTrue(region.fullScreen)
        assertEquals(1f, region.sizeAt(500f, 1000f), 0.001f)
    }
}
