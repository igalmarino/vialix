// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestGenerationTest {

    @Test
    fun `a newer request supersedes an older request for the same destination`() {
        val requests = RequestGeneration()
        val first = requests.begin()
        val retry = requests.begin()

        assertFalse(requests.isCurrent(first))
        assertTrue(requests.isCurrent(retry))
    }

    @Test
    fun `dismissing a preview invalidates its request`() {
        val requests = RequestGeneration()
        val request = requests.begin()

        requests.invalidate()

        assertFalse(requests.isCurrent(request))
    }
}
