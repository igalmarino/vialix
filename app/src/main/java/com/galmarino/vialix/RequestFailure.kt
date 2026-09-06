// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix

import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import java.io.IOException

/**
 * Why a request to the routing or geocoding server failed, reduced to what the user can act on.
 * The exception itself goes to logcat only: its message can contain host names and, for a hosted
 * Valhalla, the endpoint URL with its API key.
 */
sealed interface RequestFailure {
    /** No network, DNS failure, timeout. */
    data object Offline : RequestFailure

    /** The server answered with a non-2xx status. */
    data class ServerError(val statusCode: Int) : RequestFailure

    /** Anything else (unparseable response, empty body, bug). */
    data object Other : RequestFailure

    companion object {
        fun of(e: Throwable): RequestFailure = when (e) {
            is InvalidStatusCodeException -> ServerError(e.statusCode)
            is IOException -> Offline
            else -> Other
        }
    }
}
