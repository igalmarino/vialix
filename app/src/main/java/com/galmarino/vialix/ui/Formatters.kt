// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.icu.util.ULocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.galmarino.vialix.settings.DistanceUnits
import com.galmarino.vialix.settings.Settings
import com.stadiamaps.ferrostar.ui.formatters.DistanceFormatter
import com.stadiamaps.ferrostar.ui.formatters.DistanceMeasurementSystem
import com.stadiamaps.ferrostar.ui.formatters.DurationFormatter
import com.stadiamaps.ferrostar.ui.formatters.LocalizedDistanceFormatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToLong

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

/**
 * Travel times ("25 min", "1 hr, 25 min") in the guidance language. Ferrostar's
 * `LocalizedDurationFormatter` writes English unit letters whatever the locale, and its "25 m"
 * next to a "12 km" distance reads as metres, so durations are formatted here with ICU instead.
 */
@Composable
fun rememberDurationFormatter(settings: Settings): DurationFormatter {
    val languageTag = settings.resolvedLanguageTag()
    return remember(languageTag) { IcuDurationFormatter(ULocale.forLanguageTag(languageTag)) }
}

/** Hours and minutes through ICU's measure formatter, so unit names and their order follow [locale]. */
class IcuDurationFormatter(locale: ULocale) : DurationFormatter {
    private val measureFormat = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)

    override fun format(durationSeconds: Double): String {
        val parts = durationParts(durationSeconds)
        val measures = buildList {
            if (parts.hours > 0) add(Measure(parts.hours, MeasureUnit.HOUR))
            if (parts.minutes > 0 || parts.hours == 0L) add(Measure(parts.minutes, MeasureUnit.MINUTE))
        }
        return measureFormat.formatMeasures(*measures.toTypedArray())
    }
}

internal data class DurationParts(val hours: Long, val minutes: Long)

/** Whole hours and minutes, rounded to the nearest minute and never less than one: a travel time of "0 min" is not one. */
internal fun durationParts(seconds: Double): DurationParts {
    val totalMinutes = (seconds / 60).roundToLong().coerceAtLeast(1)
    return DurationParts(hours = totalMinutes / 60, minutes = totalMinutes % 60)
}

/** "14:32" / "2:32 PM" in the guidance language's conventions. */
@Composable
fun rememberClockTimeFormatter(settings: Settings): DateTimeFormatter {
    val languageTag = settings.resolvedLanguageTag()
    return remember(languageTag) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.forLanguageTag(languageTag))
    }
}
