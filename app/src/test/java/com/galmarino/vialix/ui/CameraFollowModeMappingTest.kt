// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import com.galmarino.vialix.navigation.CameraFollowMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraFollowModeMappingTest {

    @Test
    fun `each follow mode maps onto its Ferrostar camera mode`() {
        assertEquals(NavigationCameraMode.FREE, CameraFollowMode.FREE.toCameraMode())
        assertEquals(NavigationCameraMode.FOLLOW_USER, CameraFollowMode.FOLLOW.toCameraMode())
        assertEquals(NavigationCameraMode.FOLLOW_USER_WITH_BEARING, CameraFollowMode.HEADING.toCameraMode())
    }

    @Test
    fun `mapping round-trips`() {
        for (mode in CameraFollowMode.entries) {
            assertEquals(mode, mode.toCameraMode().toFollowMode())
        }
    }

    @Test
    fun `a manual pan or a route overview reads as not following`() {
        // Ferrostar switches to FREE itself on any gesture, and to OVERVIEW when framing a route.
        assertEquals(CameraFollowMode.FREE, NavigationCameraMode.FREE.toFollowMode())
        assertEquals(CameraFollowMode.FREE, NavigationCameraMode.OVERVIEW.toFollowMode())
    }

    @Test
    fun `the button always offers a next step, whatever Ferrostar is doing`() {
        assertEquals(NavigationCameraMode.FOLLOW_USER, NavigationCameraMode.OVERVIEW.toFollowMode().next().toCameraMode())
        assertEquals(NavigationCameraMode.FREE, NavigationCameraMode.FOLLOW_USER_WITH_BEARING.toFollowMode().next().toCameraMode())
    }
}
