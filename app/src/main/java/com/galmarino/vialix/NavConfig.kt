// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix

/**
 * Runtime configuration for the app: which routing and geocoding servers to call, which map
 * style to render, and how the app identifies itself to public servers.
 *
 * Values come from `BuildConfig`, which is populated from `local.properties` (or environment
 * variables) at build time. Anything left blank falls back to the defaults declared here, so
 * the defaults live in exactly one place.
 */
data class NavConfig(
    /** Valhalla `/route` endpoint. Any Valhalla-compatible server works. */
    val valhallaEndpoint: String = DEFAULT_VALHALLA_ENDPOINT,
    /** Valhalla costing model: `auto`, `bicycle`, `pedestrian`, ... */
    val routingProfile: String = DEFAULT_ROUTING_PROFILE,
    /** MapLibre style JSON URL for the light theme. */
    val mapStyleUrl: String = DEFAULT_MAP_STYLE_URL,
    /**
     * MapLibre style JSON URL for the dark theme, or `null` (the default) to derive the dark map
     * from [mapStyleUrl] at runtime with `NightStylePatch`; see [mapStyleUrlFor] and [derivesNightStyle].
     */
    val mapStyleUrlDark: String? = null,
    /** Sent as `X-Client-Id` on routing and geocoding requests, as the FOSSGIS demo server asks. */
    val clientId: String = DEFAULT_CLIENT_ID,
    /**
     * Whether spoken guidance starts out enabled. Only applies until the user touches the mute
     * button; after that the choice they made is what is restored.
     */
    val voiceGuidance: Boolean = DEFAULT_VOICE_GUIDANCE,
    /** Photon `/api` endpoint used by the destination search. */
    val geocoderEndpoint: String = DEFAULT_GEOCODER_ENDPOINT,
) {
    init {
        require(valhallaEndpoint.startsWith("https://") || valhallaEndpoint.startsWith("http://")) {
            "valhallaEndpoint must be an http(s) URL, got '$valhallaEndpoint'"
        }
        require(mapStyleUrl.isStyleUrl()) { "mapStyleUrl must be an http(s) or asset URL, got '$mapStyleUrl'" }
        require(mapStyleUrlDark == null || mapStyleUrlDark.isStyleUrl()) {
            "mapStyleUrlDark must be an http(s) or asset URL, got '$mapStyleUrlDark'"
        }
        require(geocoderEndpoint.startsWith("https://") || geocoderEndpoint.startsWith("http://")) {
            "geocoderEndpoint must be an http(s) URL, got '$geocoderEndpoint'"
        }
        require(routingProfile.isNotBlank()) { "routingProfile must not be blank" }
        require(clientId.isNotBlank()) { "clientId must not be blank" }
    }

    /** The style URL the map should load in the given theme; the light one when no dark style is configured. */
    fun mapStyleUrlFor(dark: Boolean): String = if (dark) mapStyleUrlDark ?: mapStyleUrl else mapStyleUrl

    /** Whether the style from [mapStyleUrlFor] must be recoloured into a night style before use. */
    fun derivesNightStyle(dark: Boolean): Boolean = dark && mapStyleUrlDark == null

    companion object {
        /** Public Valhalla instance run by FOSSGIS e.V. Fair use: ~1 request/second per user. */
        const val DEFAULT_VALHALLA_ENDPOINT = "https://valhalla1.openstreetmap.de/route"
        const val DEFAULT_ROUTING_PROFILE = "auto"

        /** OpenFreeMap "liberty" style: OpenStreetMap data, free, no API key. */
        const val DEFAULT_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val DEFAULT_CLIENT_ID = "com.galmarino.vialix"
        const val DEFAULT_VOICE_GUIDANCE = true

        /** Public Photon instance run by komoot. Fair use only; no SLA. */
        const val DEFAULT_GEOCODER_ENDPOINT = "https://photon.komoot.io/api"

        /** Builds the config from generated `BuildConfig` fields, applying defaults for blanks. */
        fun fromBuildConfig(): NavConfig = fromValues(
            valhallaEndpoint = BuildConfig.VALHALLA_ENDPOINT,
            routingProfile = BuildConfig.ROUTING_PROFILE,
            mapStyleUrl = BuildConfig.MAP_STYLE_URL,
            mapStyleUrlDark = BuildConfig.MAP_STYLE_URL_DARK,
            clientId = BuildConfig.CLIENT_ID,
            voiceGuidance = BuildConfig.VOICE_GUIDANCE,
            geocoderEndpoint = BuildConfig.GEOCODER_ENDPOINT,
        )

        /**
         * Same as [fromBuildConfig] but testable: blank strings fall back to the defaults (a blank
         * dark style means "derive it from the light one").
         */
        fun fromValues(
            valhallaEndpoint: String?,
            routingProfile: String?,
            mapStyleUrl: String?,
            mapStyleUrlDark: String?,
            clientId: String?,
            voiceGuidance: String?,
            geocoderEndpoint: String?,
        ): NavConfig = NavConfig(
            valhallaEndpoint = valhallaEndpoint.orDefault(DEFAULT_VALHALLA_ENDPOINT),
            routingProfile = routingProfile.orDefault(DEFAULT_ROUTING_PROFILE),
            mapStyleUrl = mapStyleUrl.orDefault(DEFAULT_MAP_STYLE_URL),
            mapStyleUrlDark = mapStyleUrlDark.orNull(),
            clientId = clientId.orDefault(DEFAULT_CLIENT_ID),
            voiceGuidance = voiceGuidance.orDefault(DEFAULT_VOICE_GUIDANCE),
            geocoderEndpoint = geocoderEndpoint.orDefault(DEFAULT_GEOCODER_ENDPOINT),
        )

        private fun String.isStyleUrl(): Boolean = startsWith("https://") || startsWith("http://") || startsWith("asset://")

        private fun String?.orDefault(default: String): String = orNull() ?: default

        private fun String?.orNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

        /** Anything that is not recognisably `true`/`false` falls back rather than failing a build. */
        private fun String?.orDefault(default: Boolean): Boolean = this?.trim()?.lowercase()?.toBooleanStrictOrNull() ?: default
    }
}
