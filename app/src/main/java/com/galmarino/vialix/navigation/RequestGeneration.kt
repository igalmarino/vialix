// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import java.util.concurrent.atomic.AtomicLong

/** Identifies the latest asynchronous request even when cancellation races its completion. */
internal class RequestGeneration {
    private val generation = AtomicLong()

    fun begin(): Long = generation.incrementAndGet()

    fun invalidate() {
        generation.incrementAndGet()
    }

    fun isCurrent(candidate: Long): Boolean = generation.get() == candidate
}
