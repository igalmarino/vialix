package com.galmarino.vialix.settings

import java.util.Locale

/**
 * The languages the interface is translated into (`res/values-<tag>/strings.xml`, English in
 * `values/`). Unrelated to the guidance languages in `SpokenLanguage`, which are whatever Valhalla
 * and the TTS engine can produce; the UI list is only what has actually been translated.
 */
object UiLanguage {
    const val DEFAULT_TAG = "en"

    /** BCP 47 language tags with a translation. [DEFAULT_TAG] first, then alphabetically. */
    val SUPPORTED_TAGS: List<String> = listOf(DEFAULT_TAG, "de", "es", "fr", "it")

    /**
     * The translation Android will pick for [locale] when the app follows the system: the same
     * language if there is one, else English. Used to name the "System default (…)" choice.
     */
    fun resolve(locale: Locale): String =
        SUPPORTED_TAGS.firstOrNull { it == locale.language } ?: DEFAULT_TAG
}
