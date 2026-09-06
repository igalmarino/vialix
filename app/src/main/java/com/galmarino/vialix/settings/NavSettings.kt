// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.galmarino.vialix.NavConfig
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The single store for runtime settings, persisted in `SharedPreferences`.
 *
 * First-launch values come from the build-time [NavConfig] (`routingProfile`, `voiceGuidance`)
 * and the device locale (language; units from the phone's measurement-system preference, else
 * its region); once the user changes a setting, its stored value wins. The app language is the
 * one setting not stored here on API 33+: the platform's per-app locale is its store (see
 * `AppLocales.kt`), and this class only mirrors it into [state]. Each setter persists only
 * its own key, so touching one control does not freeze the others' first-launch defaults (a stored
 * value can no longer follow a later locale or build-config change). Everything that depends on a
 * setting observes [state] rather than reading preferences itself, so a change made in the settings
 * screen reaches TTS, routing and formatting at once.
 */
class NavSettings(context: Context, defaults: NavConfig) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(load(defaults))
    val state: StateFlow<Settings> = _state.asStateFlow()

    fun setLanguageTag(tag: String?) =
        update({ copy(languageTag = tag) }) { if (tag == null) remove(KEY_LANGUAGE) else putString(KEY_LANGUAGE, tag) }

    fun setVoiceEnabled(enabled: Boolean) = update({ copy(voiceEnabled = enabled) }) { putBoolean(KEY_VOICE_ENABLED, enabled) }

    fun setRoutingProfile(profile: String) = update({ copy(routingProfile = profile) }) { putString(KEY_ROUTING_PROFILE, profile) }

    fun setUnits(units: DistanceUnits) = update({ copy(units = units) }) { putString(KEY_UNITS, units.name) }

    /** Also handed to the system (see [applyAppNightMode]) so the launch window follows the choice. */
    fun setThemeMode(mode: ThemeMode) {
        update({ copy(themeMode = mode) }) { putString(KEY_THEME_MODE, mode.name) }
        applyAppNightMode(appContext, mode)
    }

    /**
     * Persists the app language (see [writeAppLocale]). On API 33+ the framework
     * then restarts the Activity itself; below it the caller must `recreate()` for the new
     * strings to show.
     */
    fun setUiLanguageTag(tag: String?) {
        update({ copy(uiLanguageTag = tag) }) {}
        writeAppLocale(appContext, prefs, tag)
    }

    /**
     * Re-reads the app language from the system (API 33+), where Settings > Apps > Vialix >
     * Language can change it behind the app's back while the process is alive. Called on every
     * Activity creation; a no-op below 33, where nothing else writes the value.
     */
    fun syncUiLanguageFromSystem() {
        if (!systemManagesAppLocale) return
        val tag = readAppLocale(appContext, prefs)
        _state.update { if (it.uiLanguageTag == tag) it else it.copy(uiLanguageTag = tag) }
    }

    /** Session-only (see [Settings.simulateDriving]); deliberately never persisted. */
    fun setSimulateDriving(enabled: Boolean) = update({ copy(simulateDriving = enabled) }) {}

    /**
     * `true` exactly once per install: the first time the map is shown, so the "how to pick a
     * destination" hint can be surfaced as a one-off snackbar instead of a permanent banner.
     */
    fun consumeFirstLaunchHint(): Boolean {
        if (prefs.getBoolean(KEY_HINT_SHOWN, false)) return false
        prefs.edit { putBoolean(KEY_HINT_SHOWN, true) }
        return true
    }

    /** Applies [transform] to the state and [persist] to the preferences; only the changed key is written. */
    private fun update(transform: Settings.() -> Settings, persist: SharedPreferences.Editor.() -> Unit) {
        _state.update { it.transform() }
        prefs.edit { persist() }
    }

    /** Reads the raw values; the fallbacks and migrations are [Settings.restore], which is pure and tested. */
    private fun load(defaults: NavConfig): Settings = Settings.restore(
        StoredSettings(
            languageTag = prefs.getString(KEY_LANGUAGE, null),
            voiceEnabled = if (prefs.contains(KEY_VOICE_ENABLED)) prefs.getBoolean(KEY_VOICE_ENABLED, true) else null,
            routingProfile = prefs.getString(KEY_ROUTING_PROFILE, null),
            units = prefs.getString(KEY_UNITS, null),
            themeMode = prefs.getString(KEY_THEME_MODE, null),
            uiLanguageTag = readAppLocale(appContext, prefs),
        ),
        defaultVoiceEnabled = defaults.voiceGuidance,
        defaultRoutingProfile = defaults.routingProfile,
        locale = Locale.getDefault(),
    )

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_LANGUAGE = "language_tag"
        const val KEY_VOICE_ENABLED = "voice_enabled"
        const val KEY_ROUTING_PROFILE = "routing_profile"
        const val KEY_UNITS = "units"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_HINT_SHOWN = "first_launch_hint_shown"
    }
}
