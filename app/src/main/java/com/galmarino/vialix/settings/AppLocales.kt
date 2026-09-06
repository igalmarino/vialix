// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.settings

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit
import java.util.Locale

/**
 * Where the app-language choice lives and how it is applied, by API level.
 *
 * On API 33+ the platform's per-app language (`LocaleManager`) is the one store: the in-app picker
 * and Settings > Apps > Vialix > Language write the same value, the framework restarts the
 * Activity with the new locale and makes it the process default (`Locale.getDefault()`). Below 33
 * there is no such thing, so the choice is a preference that [MainActivity] applies itself in
 * `attachBaseContext` via [withAppLocale], mirroring the framework's behaviour as closely as the
 * platform allows (the foreground-service notification keeps the device language there).
 */
val systemManagesAppLocale: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private const val KEY_UI_LANGUAGE = "ui_language"

/** The stored choice, `null` when the app follows the system. Unknown tags are dropped. */
fun readAppLocale(context: Context, prefs: SharedPreferences): String? {
    val tag =
        if (systemManagesAppLocale) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            prefs.getString(KEY_UI_LANGUAGE, null)
        }
    return tag?.takeIf { it in UiLanguage.SUPPORTED_TAGS }
}

/** Persists [tag] (`null` = follow the system); on API 33+ the framework applies it too. */
fun writeAppLocale(context: Context, prefs: SharedPreferences, tag: String?) {
    if (systemManagesAppLocale) {
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    } else {
        prefs.edit { if (tag == null) remove(KEY_UI_LANGUAGE) else putString(KEY_UI_LANGUAGE, tag) }
    }
}

/** The device language, regardless of the app's own choice. */
fun systemLocale(context: Context): Locale {
    if (systemManagesAppLocale) {
        context.getSystemService(LocaleManager::class.java)?.systemLocales?.takeIf { !it.isEmpty }?.let { return it[0] }
    }
    return Resources.getSystem().configuration.locales[0]
}

/**
 * API 29–32 only: a context whose resources speak [tag]. Also moves the process default locale,
 * as the framework does on 33+, so "System default" guidance language, units and number
 * formatting follow the app language on every API level. `null` restores the device language.
 */
fun Context.withAppLocale(tag: String?): Context {
    if (tag == null) {
        Locale.setDefault(Resources.getSystem().configuration.locales[0])
        return this
    }
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration).apply { setLocales(LocaleList(locale)) }
    return createConfigurationContext(config)
}
