// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RerouteAnnouncerTest {

    @Test
    fun `announces the first time the user leaves the route`() {
        val announcer = RerouteAnnouncer()
        assertFalse(announcer.onDeviation(offRoute = false, nowMs = 0))
        assertTrue(announcer.onDeviation(offRoute = true, nowMs = 1_000))
    }

    @Test
    fun `stays quiet while the user remains off route`() {
        val announcer = RerouteAnnouncer()
        announcer.onDeviation(offRoute = true, nowMs = 0)
        assertFalse(announcer.onDeviation(offRoute = true, nowMs = 1_000))
        assertFalse(announcer.onDeviation(offRoute = true, nowMs = 20_000))
    }

    @Test
    fun `a flapping deviation flag does not repeat the announcement within the gap`() {
        val announcer = RerouteAnnouncer()
        assertTrue(announcer.onDeviation(offRoute = true, nowMs = 0))
        announcer.onDeviation(offRoute = false, nowMs = 2_000)
        assertFalse(announcer.onDeviation(offRoute = true, nowMs = 3_000))
    }

    @Test
    fun `announces again once the gap has passed`() {
        val announcer = RerouteAnnouncer()
        announcer.onDeviation(offRoute = true, nowMs = 0)
        announcer.onDeviation(offRoute = false, nowMs = 5_000)
        assertTrue(announcer.onDeviation(offRoute = true, nowMs = RerouteAnnouncer.MIN_GAP_MS))
    }

    @Test
    fun `reset forgets the previous trip`() {
        val announcer = RerouteAnnouncer()
        announcer.onDeviation(offRoute = true, nowMs = 0)
        announcer.reset()
        assertTrue(announcer.onDeviation(offRoute = true, nowMs = 1_000))
    }

    @Test
    fun `a custom gap is honoured`() {
        val announcer = RerouteAnnouncer(minGapMs = 500)
        announcer.onDeviation(offRoute = true, nowMs = 0)
        announcer.onDeviation(offRoute = false, nowMs = 100)
        assertTrue(announcer.onDeviation(offRoute = true, nowMs = 600))
    }
}
