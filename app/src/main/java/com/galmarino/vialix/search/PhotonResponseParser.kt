// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.search

import org.json.JSONObject
import uniffi.ferrostar.GeographicCoordinate

/** Photon renders labels in these languages only; any other tag must omit the `lang` parameter. */
private val PHOTON_LANGUAGES = setOf("en", "de", "fr", "it")

/** Valhalla-style tag ("de-DE") -> Photon `lang` value ("de"), or null when Photon has no such language. */
internal fun photonLanguage(languageTag: String?): String? =
    languageTag?.substringBefore('-')?.lowercase()?.takeIf { it in PHOTON_LANGUAGES }

/**
 * Turns a Photon GeoJSON response into [Place]s. Pure `org.json` so it stays unit-testable; the
 * label rules mirror what Photon's own demo page shows.
 */
internal object PhotonResponseParser {

    /** Throws `JSONException` on malformed input; features that cannot be displayed are skipped. */
    fun parse(json: String): List<Place> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return (0 until features.length())
            .mapNotNull { features.optJSONObject(it) }
            .mapNotNull(::placeOf)
            // The same town often comes back twice (place=city and boundary=administrative).
            .distinctBy { it.name to it.address }
    }

    internal fun placeOf(feature: JSONObject): Place? {
        val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        if (coordinates.length() < 2) return null
        val lng = coordinates.optDouble(0)
        val lat = coordinates.optDouble(1)
        if (lng.isNaN() || lat.isNaN()) return null

        val props = feature.optJSONObject("properties") ?: JSONObject()
        val name = displayName(props) ?: return null
        return Place(name, formatAddress(props, name), GeographicCoordinate(lat = lat, lng = lng))
    }

    private fun displayName(props: JSONObject): String? = props.text("name")
        ?: streetLine(props)
        ?: props.text("city")
        ?: props.text("district")
        ?: props.text("county")
        ?: props.text("state")
        ?: props.text("country")

    /** "street housenumber", or just the street. */
    private fun streetLine(props: JSONObject): String? {
        val street = props.text("street") ?: return null
        return listOfNotNull(street, props.text("housenumber")).joinToString(" ")
    }

    /** "postcode city" (falling back to district or county). */
    private fun localityLine(props: JSONObject): String? {
        val locality = props.text("city") ?: props.text("district") ?: props.text("county")
        return listOfNotNull(props.text("postcode"), locality).joinToString(" ").ifBlank { null }
    }

    internal fun formatAddress(props: JSONObject, name: String): String? {
        val city = props.text("city")
        // City-states (Berlin, Hamburg) repeat the city as the state.
        val state = props.text("state")?.takeIf { it != city }
        return listOfNotNull(streetLine(props), localityLine(props), state, props.text("country"))
            .filter { it != name }
            .distinct()
            .joinToString(", ")
            .ifBlank { null }
    }

    private fun JSONObject.text(key: String): String? = optString(key).trim().takeIf { it.isNotEmpty() && !isNull(key) }
}
