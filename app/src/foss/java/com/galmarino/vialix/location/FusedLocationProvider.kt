// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.location

import android.content.Context
import android.util.Log
import com.stadiamaps.ferrostar.core.location.NavigationLocationProviding

/**
 * The `foss` flavour has no Google Play Services client, so there is never a fused provider:
 * `AppGraph` gets `null` and uses the platform `LocationManager` through Ferrostar's
 * `AndroidLocationProvider`, as the `full` flavour does on GMS-free devices. Same API as
 * `src/full`'s implementation.
 */
object FusedLocationProvider {
    fun create(@Suppress("UNUSED_PARAMETER") context: Context): NavigationLocationProviding? {
        Log.i(TAG, "FOSS build; using LocationManager")
        return null
    }

    private const val TAG = "Location"
}
