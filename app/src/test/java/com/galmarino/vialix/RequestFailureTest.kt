// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix

import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import com.stadiamaps.ferrostar.core.NoResponseBodyException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class RequestFailureTest {

    @Test
    fun `network problems are offline`() {
        assertEquals(RequestFailure.Offline, RequestFailure.of(UnknownHostException("valhalla1.openstreetmap.de")))
        assertEquals(RequestFailure.Offline, RequestFailure.of(SocketTimeoutException("timeout")))
        assertEquals(RequestFailure.Offline, RequestFailure.of(IOException("Canceled")))
    }

    @Test
    fun `a non-2xx answer keeps its status code`() {
        assertEquals(RequestFailure.ServerError(429), RequestFailure.of(InvalidStatusCodeException(429)))
    }

    @Test
    fun `anything else is other`() {
        assertEquals(RequestFailure.Other, RequestFailure.of(NoResponseBodyException()))
        assertEquals(RequestFailure.Other, RequestFailure.of(IllegalStateException("bug")))
    }
}
