// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.R
import com.galmarino.vialix.settings.DistanceUnits
import java.text.NumberFormat
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import uniffi.ferrostar.UserLocation

private const val SPEED_MAX_AGE_MS = 5_000L

/** Current measured speed, independently expired if the provider stops delivering fixes. */
@Composable
internal fun SpeedReadout(location: UserLocation?, units: DistanceUnits) {
    val expiresAt = location?.timestamp?.toEpochMilli()?.plus(SPEED_MAX_AGE_MS) ?: 0L
    val expired by produceState(System.currentTimeMillis() >= expiresAt, expiresAt) {
        value = System.currentTimeMillis() >= expiresAt
        if (!value) {
            delay((expiresAt - System.currentTimeMillis()).coerceAtLeast(0))
            value = true
        }
    }
    val speed = roundedSpeed(location?.speed?.value.takeUnless { expired }, units)
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val number = speed?.let(formatter::format) ?: stringResource(R.string.speed_unknown)
    val unit = stringResource(if (units == DistanceUnits.METRIC) R.string.speed_kmh else R.string.speed_mph)
    val description =
        if (speed == null) stringResource(R.string.speed_unavailable) else stringResource(R.string.current_speed, number, unit)
    val chrome = mapChrome()
    Surface(
        modifier = Modifier.padding(12.dp).clearAndSetSemantics { contentDescription = description },
        shape = RoundedCornerShape(16.dp),
        color = chrome.surface,
        contentColor = chrome.content,
        border = chrome.outlineStroke,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(number, style = MaterialTheme.typography.headlineMedium)
            Text(unit, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Missing or invalid speed is unknown, not a stationary vehicle. */
internal fun roundedSpeed(metresPerSecond: Double?, units: DistanceUnits): Long? {
    if (metresPerSecond == null || !metresPerSecond.isFinite() || metresPerSecond < 0) return null
    val multiplier = if (units == DistanceUnits.METRIC) 3.6 else 3.6 / 1.609344
    return (metresPerSecond * multiplier).roundToLong()
}
