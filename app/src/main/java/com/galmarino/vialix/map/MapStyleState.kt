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

    /** Download failed, timed out, or the style cannot be inlined: load [styleUrl] as-is. */
    data class Unavailable(val styleUrl: String) : MapStyleState
}
