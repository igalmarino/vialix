package com.galmarino.vialix.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.R
import com.galmarino.vialix.navigation.Destination
import com.galmarino.vialix.navigation.RouteError
import com.galmarino.vialix.navigation.arrivalTime
import com.galmarino.vialix.navigation.durationSeconds
import com.galmarino.vialix.navigation.viaName
import com.stadiamaps.ferrostar.ui.formatters.DistanceFormatter
import com.stadiamaps.ferrostar.ui.formatters.DurationFormatter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route

/**
 * What the bottom sheet shows once a destination is chosen (long-press, search result, Home/Work
 * chip or recent): where we are going, how to get there (car / bicycle / walking), how far/long
 * the route is and when it arrives, the alternatives Valhalla offered (as chips, when there is more
 * than one route), then Start / Cancel. A failed request is shown here with a Retry rather than as
 * a snackbar. The sheet itself is owned by `NavigationScreen`; this is only its content.
 *
 * Distances and durations are rendered by the same Ferrostar formatters as the guidance banner,
 * so the preview, the chips and the trip progress view agree on units, digits and wording.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePreviewSheetContent(
    destination: Destination,
    routeOptions: List<Route>,
    selectedRoute: Int,
    isFetchingRoute: Boolean,
    error: RouteError?,
    routingProfile: String,
    distanceFormatter: DistanceFormatter,
    durationFormatter: DurationFormatter,
    clockFormatter: DateTimeFormatter,
    onProfileSelected: (String) -> Unit,
    onRouteSelected: (Int) -> Unit,
    onRetry: () -> Unit,
    onStartNavigation: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val route = routeOptions.getOrNull(selectedRoute)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
        Text(
            text = destination.name ?: stringResource(R.string.dropped_pin_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = destination.address ?: formatCoordinates(destination.coordinate),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Car / bicycle / walking. Writes the routing profile setting; the ViewModel re-requests
        // the preview when it changes, and the same value drives guidance thresholds later.
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            ROUTING_PROFILES.forEachIndexed { index, profile ->
                val selected = profile.costing == routingProfile
                val label = stringResource(profile.labelRes)
                // Icon-only segments: the profile icon in the icon slot, replaced by Material's
                // checkmark while selected; the label stays for TalkBack only.
                //
                // Material3's SegmentedButton takes the height of its icon+label row from the
                // label alone, so an empty label gives a 0 dp row and the icon is drawn (clipped)
                // above it. The label is therefore a zero-width spacer as tall as the icon. The row
                // also always reserves the icon-to-label gap (8 dp) to the right of the icon, so the
                // icon is nudged by half of it to sit in the centre of the segment.
                SegmentedButton(
                    selected = selected,
                    onClick = { onProfileSelected(profile.costing) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = ROUTING_PROFILES.size),
                    icon = {
                        Box(modifier = Modifier.offset(x = 4.dp)) {
                            SegmentedButtonDefaults.Icon(active = selected) {
                                Icon(painter = painterResource(profile.iconRes), contentDescription = null)
                            }
                        }
                    },
                    label = { Spacer(Modifier.height(SegmentedButtonDefaults.IconSize)) },
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }

        if (routeOptions.size > 1) {
            RouteOptionChips(routeOptions, selectedRoute, durationFormatter, onRouteSelected)
        }

        Row(modifier = Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                route != null -> RouteSummary(route, distanceFormatter, durationFormatter, clockFormatter)
                isFetchingRoute -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(text = stringResource(R.string.fetching_route), style = MaterialTheme.typography.bodyMedium)
                }
                error != null -> {
                    Text(
                        text = errorText(error),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        }

        Button(
            onClick = onStartNavigation,
            enabled = route != null,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.start_navigation))
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

/** One chip per alternative: "25 min · via A1"; the selected one is filled. */
@Composable
private fun RouteOptionChips(
    routes: List<Route>,
    selected: Int,
    durationFormatter: DurationFormatter,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        routes.forEachIndexed { index, route ->
            val via = route.viaName()
            val label = durationFormatter.format(route.durationSeconds)
            FilterChip(
                selected = index == selected,
                onClick = { onSelect(index) },
                label = { Text(if (via != null) stringResource(R.string.route_summary, label, stringResource(R.string.route_via, via)) else label) },
            )
        }
    }
}

/** "25 min" over "12 km · Arrive 14:32". */
@Composable
private fun RouteSummary(
    route: Route,
    distanceFormatter: DistanceFormatter,
    durationFormatter: DurationFormatter,
    clockFormatter: DateTimeFormatter,
) {
    val duration = route.durationSeconds
    Column {
        Text(text = durationFormatter.format(duration), style = MaterialTheme.typography.titleLarge)
        Text(
            text =
                stringResource(
                    R.string.route_summary,
                    distanceFormatter.format(route.distance),
                    stringResource(R.string.arrive_at, clockFormatter.format(arrivalTime(LocalDateTime.now(), duration))),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "52.52000, 13.40500": the fallback label for a long-pressed point with no address. Always with a
 * decimal point: in a comma-decimal locale "52,52000, 13,40500" is unreadable, and coordinates are
 * conventionally written this way everywhere.
 */
internal fun formatCoordinates(coordinate: GeographicCoordinate): String =
    String.format(Locale.ROOT, "%.5f, %.5f", coordinate.lat, coordinate.lng)
