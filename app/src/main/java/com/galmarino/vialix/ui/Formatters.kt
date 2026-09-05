package com.galmarino.vialix.ui

import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.galmarino.vialix.settings.DistanceUnits
import com.galmarino.vialix.settings.Settings
import com.stadiamaps.ferrostar.ui.formatters.DistanceFormatter
import com.stadiamaps.ferrostar.ui.formatters.DistanceMeasurementSystem
import com.stadiamaps.ferrostar.ui.formatters.LocalizedDistanceFormatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Ferrostar's distance formatter, following the units setting and the guidance language (the
 * banner text is in that language, so its numbers should be too). One instance is shared by the
 * guidance banner, the preview sheet, the Home/Work chips and the search results.
 */
@Composable
fun rememberDistanceFormatter(settings: Settings): DistanceFormatter {
    val languageTag = settings.resolvedLanguageTag()
    return remember(languageTag, settings.units) {
        LocalizedDistanceFormatter(
            localeOverride = ULocale.forLanguageTag(languageTag),
            distanceMeasurementSystemOverride =
                when (settings.units) {
                    DistanceUnits.METRIC -> DistanceMeasurementSystem.SI
                    DistanceUnits.IMPERIAL -> DistanceMeasurementSystem.IMPERIAL
                },
        )
    }
}

/** "14:32" / "2:32 PM" in the guidance language's conventions. */
@Composable
fun rememberClockTimeFormatter(settings: Settings): DateTimeFormatter {
    val languageTag = settings.resolvedLanguageTag()
    return remember(languageTag) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.forLanguageTag(languageTag))
    }
}
