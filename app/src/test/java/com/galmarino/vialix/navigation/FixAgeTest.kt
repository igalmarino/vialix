// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.UserLocation

class FixAgeTest {

    private val now = Instant.ofEpochMilli(1_700_000_000_000L)

    private fun fix(age: Duration) = UserLocation(
        coordinates = GeographicCoordinate(lat = 48.8566, lng = 2.3522),
        horizontalAccuracy = 5.0,
        courseOverGround = null,
        timestamp = now.minus(age),
        speed = null,
    )

    @Test
    fun `a recent fix is fresh`() {
        assertTrue(fix(Duration.ofSeconds(30)).isFresh(now))
        assertTrue(fix(MAX_ORIGIN_FIX_AGE).isFresh(now))
    }

    @Test
    fun `a fix older than the limit is stale`() {
        assertFalse(fix(MAX_ORIGIN_FIX_AGE.plusMillis(1)).isFresh(now))
        assertFalse(fix(Duration.ofHours(3)).isFresh(now))
    }

    @Test
    fun `a fix from the future counts as fresh`() {
        assertTrue(fix(Duration.ofSeconds(-5)).isFresh(now))
    }

    @Test
    fun `the limit can be chosen`() {
        assertFalse(fix(Duration.ofSeconds(30)).isFresh(now, maxAge = Duration.ofSeconds(10)))
    }
}
