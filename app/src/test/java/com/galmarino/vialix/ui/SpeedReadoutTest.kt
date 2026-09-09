// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import com.galmarino.vialix.settings.DistanceUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedReadoutTest {
    @Test
    fun `converts metres per second to whole kilometres or miles per hour`() {
        assertEquals(100L, roundedSpeed(27.7777778, DistanceUnits.METRIC))
        assertEquals(60L, roundedSpeed(26.8224, DistanceUnits.IMPERIAL))
        assertEquals(4L, roundedSpeed(1.0, DistanceUnits.METRIC))
    }

    @Test
    fun `stationary is zero in either unit system`() {
        DistanceUnits.entries.forEach { assertEquals(0L, roundedSpeed(0.0, it)) }
    }

    @Test
    fun `missing negative and nonfinite speeds are unknown`() {
        DistanceUnits.entries.forEach { units ->
            listOf(null, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
                assertNull(roundedSpeed(it, units))
            }
        }
    }
}
