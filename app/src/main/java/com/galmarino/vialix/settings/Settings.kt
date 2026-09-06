// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.settings

import com.galmarino.vialix.voice.SpokenLanguage
import java.util.Locale

/**
 * User-adjustable settings. A plain value so it can be reasoned about and tested without Android;
 * [NavSettings] is the store that persists it.
 */
data class Settings(
    /** Guidance language as a Valhalla tag (`es-ES`, ...); `null` follows the device locale. */
    val languageTag: String? = null,
    /** Spoken announcements on. Mirrors Ferrostar's mute button; the two can never disagree. */
    val voiceEnabled: Boolean = true,
    /** Valhalla costing model: `auto`, `bicycle`, `pedestrian`, ... */
    val routingProfile: String = "auto",
    val units: DistanceUnits = DistanceUnits.METRIC,
    /**
     * Debug builds only: replay the route instead of using real GPS. Lives here so the Settings
     * screen can toggle it like any other setting, but it is never persisted — a fresh process
     * always starts on real positions.
     */
    val simulateDriving: Boolean = false,
    /** Light or dark, or whatever the system says. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Interface language (`es`, `de`, ...; see [UiLanguage]); `null` follows the device language. */
    val uiLanguageTag: String? = null,
) {
    /** The tag actually sent to Valhalla and handed to the TTS engine. */
    fun resolvedLanguageTag(locale: Locale = Locale.getDefault()): String = languageTag ?: SpokenLanguage.valhallaTag(locale)

    /**
     * Whether the app should draw itself dark, given whether the system currently is. The one
     * answer shared by the Material theme, the basemap style, the chrome over the map and the
     * system-bar glyphs, so they cannot disagree.
     */
    fun resolvesToDark(systemDark: Boolean): Boolean = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    companion object {
        /**
         * Rebuilds the settings from what the store holds ([stored]), falling back per key to the
         * first-launch defaults. Values a newer build no longer understands fall back too rather
         * than being sent on blindly: a guidance language Valhalla dropped, a routing profile the
         * app stopped offering, an enum name that no longer exists.
         */
        fun restore(stored: StoredSettings, defaultVoiceEnabled: Boolean, defaultRoutingProfile: String, locale: Locale): Settings =
            Settings(
                languageTag = stored.languageTag?.takeIf { it in SpokenLanguage.SUPPORTED_TAGS },
                voiceEnabled = stored.voiceEnabled ?: defaultVoiceEnabled,
                routingProfile = stored.routingProfile?.takeUnless { it in RETIRED_ROUTING_PROFILES } ?: defaultRoutingProfile,
                units =
                    stored.units?.let { name -> DistanceUnits.entries.firstOrNull { it.name == name } } ?: DistanceUnits.forLocale(locale),
                themeMode = stored.themeMode?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
                uiLanguageTag = stored.uiLanguageTag,
            )

        /** Costing models once offered in Settings; a stored choice falls back to the default. */
        val RETIRED_ROUTING_PROFILES: Set<String> = setOf("motorcycle")
    }
}

/** The raw preference values behind [Settings], each `null` when the user never touched the control. */
data class StoredSettings(
    val languageTag: String? = null,
    val voiceEnabled: Boolean? = null,
    val routingProfile: String? = null,
    val units: String? = null,
    val themeMode: String? = null,
    val uiLanguageTag: String? = null,
)

/** The Appearance setting. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class DistanceUnits(
    /** Value of Valhalla's `units` option, which decides the distances in the instruction text. */
    val valhallaUnits: String,
) {
    METRIC("kilometers"),
    IMPERIAL("miles"),
    ;

    companion object {
        /** Countries whose road signage is in miles (CLDR's non-metric measurement regions). */
        private val IMPERIAL_COUNTRIES = setOf("US", "GB", "LR", "MM")

        /**
         * The first-launch default. The phone's own "Measurement system" regional preference wins:
         * Android (14+) appends it to the default locale as the `ms` unicode extension
         * (`es-CL-u-ms-ussystem`), so a metric region with a miles preference gets miles and vice
         * versa. Without one, the region decides.
         */
        fun forLocale(locale: Locale): DistanceUnits = when (locale.getUnicodeLocaleType("ms")) {
            "ussystem", "uksystem" -> IMPERIAL
            "metric" -> METRIC
            else -> if (locale.country.uppercase(Locale.ROOT) in IMPERIAL_COUNTRIES) IMPERIAL else METRIC
        }
    }
}
