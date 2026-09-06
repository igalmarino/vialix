// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.search

import uniffi.ferrostar.GeographicCoordinate

/** Forward and reverse geocoding: free text in, candidate places out; a coordinate in, its address out. */
interface Geocoder {
    /**
     * Searches for [query]. [bias] nudges the ranking towards places near the user; [languageTag]
     * is a Valhalla-style tag ("de-DE") for the labels, or null for the server default. Throws on
     * network, server or parsing failure.
     */
    suspend fun search(query: String, bias: GeographicCoordinate?, languageTag: String?): List<Place>

    /**
     * The nearest addressable place to [coordinate] (used to label a long-pressed point), or null
     * when the server knows nothing there. Throws like [search].
     */
    suspend fun reverse(coordinate: GeographicCoordinate, languageTag: String?): Place?
}
