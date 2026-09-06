// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.voice

import java.util.Locale

/**
 * Picks the language for spoken (and written) guidance.
 *
 * Valhalla renders instructions from a fixed set of language tags and silently falls back to
 * English for anything else, so a regional locale has to be mapped onto the closest supported
 * tag before it is sent. The same tag is handed to the TTS engine, which is what keeps the
 * instruction text and the voice reading it in the same language.
 */
internal object SpokenLanguage {

    /** Fallback for locales Valhalla does not render. */
    const val DEFAULT_TAG = "en-US"

    /**
     * Language tags supported by Valhalla's OSRM narrative builder. Order matters: the first
     * entry for a language subtag is the one an unlisted region falls back to (so `en-AU` becomes
     * `en-US`, `pt-AO` becomes `pt-PT`).
     */
    val SUPPORTED_TAGS =
        listOf(
            "en-US", "en-GB",
            "bg-BG", "ca-ES", "cs-CZ", "da-DK", "de-DE", "el-GR", "es-ES", "et-EE", "fi-FI",
            "fr-FR", "hi-IN", "hu-HU", "it-IT", "ja-JP", "nb-NO", "nl-NL", "pl-PL", "pt-PT",
            "pt-BR", "ro-RO", "ru-RU", "sk-SK", "sl-SI", "sv-SE", "tr-TR", "uk-UA",
        )

    /**
     * The supported tag closest to [locale]: an exact match if there is one, otherwise the first
     * tag sharing its language subtag, otherwise [DEFAULT_TAG].
     */
    fun valhallaTag(locale: Locale = Locale.getDefault()): String {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language.isEmpty()) return DEFAULT_TAG

        val country = locale.country.uppercase(Locale.ROOT)
        val exact = "$language-$country"

        return SUPPORTED_TAGS.firstOrNull { it.equals(exact, ignoreCase = true) }
            ?: SUPPORTED_TAGS.firstOrNull { it.substringBefore('-').equals(language, ignoreCase = true) }
            ?: DEFAULT_TAG
    }
}
