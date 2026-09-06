// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraFollowModeTest {

    @Test
    fun `tapping cycles free, follow, heading, free`() {
        assertEquals(CameraFollowMode.FOLLOW, CameraFollowMode.FREE.next())
        assertEquals(CameraFollowMode.HEADING, CameraFollowMode.FOLLOW.next())
        assertEquals(CameraFollowMode.FREE, CameraFollowMode.HEADING.next())
    }

    @Test
    fun `three taps return to the starting mode`() {
        for (start in CameraFollowMode.entries) {
            assertEquals(start, start.next().next().next())
        }
    }

    @Test
    fun `only free is not following`() {
        assertFalse(CameraFollowMode.FREE.isFollowing)
        assertTrue(CameraFollowMode.FOLLOW.isFollowing)
        assertTrue(CameraFollowMode.HEADING.isFollowing)
    }
}
