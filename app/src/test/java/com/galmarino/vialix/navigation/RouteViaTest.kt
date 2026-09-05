package com.galmarino.vialix.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.ferrostar.BoundingBox
import uniffi.ferrostar.DrivingSide
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep

class RouteViaTest {

    private fun step(distance: Double, roadName: String?) =
        RouteStep(
            geometry = emptyList(),
            distance = distance,
            duration = distance / 10,
            roadName = roadName,
            exits = emptyList(),
            instruction = "Continue",
            visualInstructions = emptyList(),
            spokenInstructions = emptyList(),
            annotations = null,
            incidents = emptyList(),
            drivingSide = DrivingSide.RIGHT,
            roundaboutExitNumber = null,
        )

    private fun route(vararg steps: RouteStep): Route {
        val origin = GeographicCoordinate(lat = 0.0, lng = 0.0)
        return Route(
            geometry = listOf(origin),
            bbox = BoundingBox(sw = origin, ne = origin),
            distance = steps.sumOf { it.distance },
            waypoints = emptyList(),
            steps = steps.toList(),
        )
    }

    @Test
    fun `the longest named step names the route`() {
        val route = route(step(200.0, "Ramp"), step(5000.0, "A1"), step(900.0, "High Street"))
        assertEquals("A1", route.viaName())
    }

    @Test
    fun `unnamed steps are skipped even when longest`() {
        val route = route(step(8000.0, null), step(300.0, ""), step(400.0, "Main Road"))
        assertEquals("Main Road", route.viaName())
    }

    @Test
    fun `no named step yields null`() {
        assertNull(route(step(100.0, null)).viaName())
        assertNull(route().viaName())
    }
}
