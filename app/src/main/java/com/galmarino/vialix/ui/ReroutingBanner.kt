package com.galmarino.vialix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.R
import com.stadiamaps.ferrostar.core.NavigationUiState
import uniffi.ferrostar.DeviationKind
import uniffi.ferrostar.RouteDeviation

/**
 * Whether guidance is waiting for a new route: the core reports the user completely off route
 * (it keeps asking for routes by itself while that holds) or has a request in flight. The
 * in-flight flag alone is not enough, because the core only publishes it on the next location
 * update, a second late.
 */
fun NavigationUiState.isRerouting(): Boolean {
    val deviation = routeDeviation
    val offRoute = deviation is RouteDeviation.Deviation && deviation.kind is DeviationKind.CompletelyOffRoute
    return offRoute || isCalculatingNewRoute == true
}

/** Takes the instruction banner's slot while the user is off route and a new route is on its way. */
@Composable
fun ReroutingBanner(modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            Text(text = stringResource(R.string.rerouting), style = MaterialTheme.typography.titleMedium)
        }
    }
}
