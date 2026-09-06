// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.R
import com.galmarino.vialix.navigation.Arrival
import com.stadiamaps.ferrostar.ui.formatters.DistanceFormatter
import com.stadiamaps.ferrostar.ui.formatters.DurationFormatter

/**
 * What the bottom sheet shows once Ferrostar reports the trip complete: where the user got to and
 * what it took, plus Done. The sheet itself is owned by `NavigationScreen`; this is only its content.
 */
@Composable
fun ArrivalSheetContent(
    arrival: Arrival,
    distanceFormatter: DistanceFormatter,
    durationFormatter: DurationFormatter,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_flag),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Text(text = stringResource(R.string.arrived_title), style = MaterialTheme.typography.headlineSmall)
        }

        val destination = arrival.destination
        if (destination != null) {
            Text(
                text = destination.name ?: stringResource(R.string.dropped_pin_title),
                modifier = Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = destination.address ?: formatCoordinates(destination.coordinate),
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text =
                stringResource(
                    R.string.route_summary,
                    distanceFormatter.format(arrival.distanceTraveledMeters),
                    durationFormatter.format(arrival.durationSeconds),
                ),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.done))
        }
    }
}
