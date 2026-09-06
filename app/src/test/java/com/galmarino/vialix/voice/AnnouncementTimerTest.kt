// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class AnnouncementTimerTest {

    @Test
    fun `nothing to wait for before any announcement`() {
        assertEquals(0, AnnouncementTimer(1500).remainingMs(nowMs = 10_000))
    }

    @Test
    fun `counts down from the budget`() {
        val timer = AnnouncementTimer(1500)
        timer.started(nowMs = 10_000)
        assertEquals(1500, timer.remainingMs(nowMs = 10_000))
        assertEquals(900, timer.remainingMs(nowMs = 10_600))
        assertEquals(0, timer.remainingMs(nowMs = 11_500))
        assertEquals(0, timer.remainingMs(nowMs = 20_000))
    }

    @Test
    fun `a new announcement restarts the budget`() {
        val timer = AnnouncementTimer(1500)
        timer.started(nowMs = 10_000)
        timer.started(nowMs = 11_000)
        assertEquals(1500, timer.remainingMs(nowMs = 11_000))
    }
}
