// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.places

import com.galmarino.vialix.navigation.Destination
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import uniffi.ferrostar.GeographicCoordinate

/**
 * JSON (de)serialisation of [SavedPlaces] for the key-value store. Decoding is lenient: anything
 * unreadable yields an empty [SavedPlaces] rather than crashing the app over a corrupt preference.
 */
object SavedPlacesCodec {

    fun encode(places: SavedPlaces): String = JSONObject()
        .apply {
            put(KEY_VERSION, VERSION)
            places.home?.let { put(KEY_HOME, it.toJson()) }
            places.work?.let { put(KEY_WORK, it.toJson()) }
            put(KEY_RECENTS, JSONArray().apply { places.recents.forEach { put(it.toJson()) } })
        }
        .toString()

    fun decode(json: String?): SavedPlaces {
        if (json.isNullOrBlank()) return SavedPlaces()
        return try {
            val root = JSONObject(json)
            val recents = root.optJSONArray(KEY_RECENTS) ?: JSONArray()
            SavedPlaces(
                home = root.optJSONObject(KEY_HOME)?.toDestination(),
                work = root.optJSONObject(KEY_WORK)?.toDestination(),
                recents =
                    (0 until recents.length())
                        .mapNotNull { recents.optJSONObject(it)?.toDestination() }
                        .take(SavedPlaces.MAX_RECENTS),
            )
        } catch (e: JSONException) {
            SavedPlaces()
        }
    }

    private fun Destination.toJson(): JSONObject = JSONObject().apply {
        put(KEY_LAT, coordinate.lat)
        put(KEY_LNG, coordinate.lng)
        name?.let { put(KEY_NAME, it) }
        address?.let { put(KEY_ADDRESS, it) }
    }

    /** `null` when the coordinate is missing or not a number. */
    private fun JSONObject.toDestination(): Destination? {
        if (!has(KEY_LAT) || !has(KEY_LNG)) return null
        val lat = optDouble(KEY_LAT)
        val lng = optDouble(KEY_LNG)
        if (lat.isNaN() || lng.isNaN()) return null
        return Destination(
            coordinate = GeographicCoordinate(lat = lat, lng = lng),
            name = optString(KEY_NAME).takeIf { has(KEY_NAME) && it.isNotEmpty() },
            address = optString(KEY_ADDRESS).takeIf { has(KEY_ADDRESS) && it.isNotEmpty() },
        )
    }

    private const val VERSION = 1
    private const val KEY_VERSION = "version"
    private const val KEY_HOME = "home"
    private const val KEY_WORK = "work"
    private const val KEY_RECENTS = "recents"
    private const val KEY_LAT = "lat"
    private const val KEY_LNG = "lng"
    private const val KEY_NAME = "name"
    private const val KEY_ADDRESS = "address"
}
