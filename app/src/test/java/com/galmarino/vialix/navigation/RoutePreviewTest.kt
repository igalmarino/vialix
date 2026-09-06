// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class RoutePreviewTest {

    private val a = testRoute(testStep(1000.0, "A1"))
    private val b = testRoute(testStep(1200.0, "B2"))

    @Test
    fun `routes from the server become a ready preview with the first one selected`() {
        val preview = RoutePreview.of(listOf(a, b))
        assertEquals(RoutePreview.Ready(listOf(a, b), selected = 0), preview)
        assertSame(a, preview.route)
        assertEquals(listOf(a, b), preview.routeOptions)
        assertEquals(0, preview.selectedIndex)
    }

    @Test
    fun `no routes is a failure, not an empty selection`() {
        assertEquals(RoutePreview.Failed(RouteError.NoRouteFound), RoutePreview.of(emptyList()))
    }

    @Test
    fun `selecting an alternative keeps the options`() {
        val selected = RoutePreview.of(listOf(a, b)).select(1)
        assertEquals(RoutePreview.Ready(listOf(a, b), selected = 1), selected)
        assertSame(b, selected.route)
        assertEquals(1, selected.selectedIndex)
    }

    @Test
    fun `selecting out of range changes nothing`() {
        val ready = RoutePreview.of(listOf(a, b))
        assertSame(ready, ready.select(2))
        assertSame(ready, ready.select(-1))
    }

    @Test
    fun `only a ready preview has a selection`() {
        for (preview in listOf(RoutePreview.None, RoutePreview.Fetching, RoutePreview.Failed(RouteError.NoLocationFix))) {
            assertSame(preview, preview.select(0))
            assertNull(preview.route)
            assertEquals(emptyList<Any>(), preview.routeOptions)
            assertEquals(0, preview.selectedIndex)
        }
    }

    @Test
    fun `a ready preview cannot be built inconsistently`() {
        assertThrows(IllegalArgumentException::class.java) { RoutePreview.Ready(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { RoutePreview.Ready(listOf(a), selected = 1) }
    }
}
