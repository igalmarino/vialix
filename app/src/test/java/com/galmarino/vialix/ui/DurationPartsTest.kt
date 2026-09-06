// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationPartsTest {

    @Test
    fun `splits into hours and minutes`() {
        assertEquals(DurationParts(hours = 0, minutes = 25), durationParts(25 * 60.0))
        assertEquals(DurationParts(hours = 1, minutes = 25), durationParts(85 * 60.0))
        assertEquals(DurationParts(hours = 30, minutes = 0), durationParts(30 * 3600.0))
    }

    @Test
    fun `rounds to the nearest minute`() {
        assertEquals(DurationParts(hours = 0, minutes = 1), durationParts(89.9))
        assertEquals(DurationParts(hours = 0, minutes = 2), durationParts(90.0))
        assertEquals(DurationParts(hours = 1, minutes = 0), durationParts(3600.0 - 20))
    }

    @Test
    fun `never reads as zero minutes`() {
        assertEquals(DurationParts(hours = 0, minutes = 1), durationParts(0.0))
        assertEquals(DurationParts(hours = 0, minutes = 1), durationParts(12.0))
        assertEquals(DurationParts(hours = 0, minutes = 1), durationParts(-5.0))
    }
}
