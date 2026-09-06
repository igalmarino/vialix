// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import com.galmarino.vialix.navigation.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingProfilesTest {

    @Test
    fun `offered costings are distinct and each has a travel mode, a label and an icon`() {
        assertEquals(ROUTING_PROFILES.size, ROUTING_PROFILES.map { it.costing }.toSet().size)
        assertEquals(TravelMode.entries.toSet(), ROUTING_PROFILES.map { TravelMode.forProfile(it.costing) }.toSet())
        for (profile in ROUTING_PROFILES) {
            assertTrue(profile.costing, profile.labelRes != 0)
            assertTrue(profile.costing, profile.iconRes != 0)
        }
    }

    @Test
    fun `car comes first, as the first-launch default`() {
        assertEquals("auto", ROUTING_PROFILES.first().costing)
    }
}
