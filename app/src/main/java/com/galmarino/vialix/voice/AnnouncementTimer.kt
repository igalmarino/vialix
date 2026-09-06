// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.voice

/**
 * Tracks how long the last announcement still needs, given a fixed budget per announcement. The
 * writer (the ViewModel, main thread) and the reader (Ferrostar's reroute processor, its own
 * thread) are different, hence the volatile field. Pure, so the arithmetic is unit-tested.
 */
class AnnouncementTimer(private val budgetMs: Long) {
    @Volatile private var startedAt: Long? = null

    /** An announcement began at [nowMs]. */
    fun started(nowMs: Long) {
        startedAt = nowMs
    }

    /** Milliseconds the announcement started last still needs at [nowMs]; 0 when none has started or it is over. */
    fun remainingMs(nowMs: Long): Long {
        val started = startedAt ?: return 0
        return (started + budgetMs - nowMs).coerceAtLeast(0)
    }
}
