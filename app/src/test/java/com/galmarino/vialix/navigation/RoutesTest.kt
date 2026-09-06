// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {

    @Test
    fun `trip duration is the sum of the step durations`() {
        val route = testRoute(testStep(1000.0, duration = 120.0), testStep(500.0, duration = 30.5), testStep(10.0, duration = 0.0))
        assertEquals(150.5, route.durationSeconds, 0.0)
    }

    @Test
    fun `a route without steps takes no time`() {
        assertEquals(0.0, testRoute().durationSeconds, 0.0)
    }
}
