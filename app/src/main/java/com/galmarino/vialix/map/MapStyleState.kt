// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.map

/**
 * What the map should load. The screen holds the map on an empty style while [Loading], so the
 * one style load the user sees is the patched one; only if the download fails or takes too long
 * does it fall back to the plain style URL and lose the POI label fix. A later theme change leaves
 * the current style in place until its replacement has been downloaded, so the map swaps once.
 */
sealed interface MapStyleState {
    data object Loading : MapStyleState

    /** The downloaded style with [PoiLabelStylePatch] and, in the dark theme, [NightStylePatch] applied. */
    data class Patched(val json: String) : MapStyleState

    /**
     * Load [styleUrl] as-is. [downloadFailed] when the style could not be fetched (offline, error,
     * timed out), in which case the plain URL will most likely not load either and the user is
     * offered a retry; `false` when it downloaded fine but cannot be inlined (relative URLs).
     */
    data class Unavailable(val styleUrl: String, val downloadFailed: Boolean) : MapStyleState
}
