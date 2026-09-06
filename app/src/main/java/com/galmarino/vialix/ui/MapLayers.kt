// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.Path
import androidx.compose.ui.graphics.vector.PathData
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.places.FavoriteKind
import com.galmarino.vialix.places.SavedPlaces
import com.galmarino.vialix.ui.theme.LocationBlue
import com.galmarino.vialix.ui.theme.LocationBlueDark
import com.stadiamaps.ferrostar.maplibreui.NavigationMapPuckStyle
import com.stadiamaps.ferrostar.maplibreui.routeline.BorderedPolyline
import kotlin.math.cos
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.value.CirclePitchAlignment
import org.maplibre.compose.expressions.value.IconPitchAlignment
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.MaplibreComposable
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.UserLocation

/**
 * Everything the app itself draws into the map, per theme. The light values are tuned for the pale
 * "liberty" style, the dark ones for its night version from `NightStylePatch` (ground about
 * `#1e1b18`, minor roads about `#4c4c4c`): lighter blue and red so they read, a grey for the
 * alternatives that sits above the roads but well below the selected route, a light one for the
 * selected route so it shows at all. Rings stay white in both — the map-puck convention, and the
 * strongest contrast. [favorite] fills the Home/Work markers: a teal that is neither the puck's blue
 * nor the pin's red; its ring and glyph reuse [pinRing].
 */
data class MapPaint(
    val location: Color,
    val locationRing: Color,
    val pin: Color,
    val pinRing: Color,
    val alternativeRoute: Color,
    val selectedRoute: Color,
    val favorite: Color,
)

/** Pure, so it can be unit-tested; the screen passes the result down to the layers. */
fun mapPaint(dark: Boolean): MapPaint = if (dark) DarkMapPaint else LightMapPaint

private val LightMapPaint =
    MapPaint(
        location = LocationBlue,
        locationRing = Color.White,
        pin = Color(0xFFD32F2F),
        pinRing = Color.White,
        alternativeRoute = Color(0xFFB4BCC6),
        selectedRoute = Color(0xFF5B6B7C),
        favorite = Color(0xFF00796B),
    )

private val DarkMapPaint =
    MapPaint(
        location = LocationBlueDark,
        locationRing = Color.White,
        pin = Color(0xFFEF5350),
        pinRing = Color.White,
        alternativeRoute = Color(0xFF6F7A86),
        selectedRoute = Color(0xFF9AA7B4),
        favorite = Color(0xFF4DB6AC),
    )

/**
 * Ferrostar's own puck (the arrow drawn while navigating) in the same blue as [LocationPuckLayer],
 * so the two do not visibly swap colours when guidance starts.
 */
fun navigationPuckStyle(paint: MapPaint): NavigationMapPuckStyle = NavigationMapPuckStyle(
    dotFillColorCurrentLocation = paint.location,
    dotFillColorOldLocation = paint.location,
    dotStrokeColor = paint.locationRing,
    accuracyStrokeColor = paint.location.copy(alpha = 0.4f),
    accuracyFillColor = paint.location.copy(alpha = 0.12f),
    bearingColor = paint.location,
    dotRadius = 6.dp,
    dotStrokeWidth = 2.5.dp,
)

/**
 * The user's position while browsing the map: accuracy circle, heading cone and centre dot.
 *
 * Drawn by hand rather than through MapLibre's `LocationComponent`/Ferrostar's puck style because
 * neither exposes the cone: the stock bearing indicator is a triangle the size of the dot. Ferrostar
 * keeps drawing its own arrow puck once navigation starts (see `showDefaultPuck`).
 */
@Composable
@MaplibreComposable
fun LocationPuckLayer(location: UserLocation?, cameraState: CameraState, paint: MapPaint) {
    if (location == null) return
    val color = paint.location

    val source = rememberGeoJsonSource(GeoJsonData.JsonString(pointFeatureCollectionJson(location.coordinates)))
    val bearingDegrees = location.courseOverGround?.degrees?.toFloat()

    // The accuracy circle is drawn in map metres; only worth showing once it is bigger than the dot.
    // (metersPerDpAtTarget is 0 until the map has a projection.)
    val metersPerDp = cameraState.metersPerDpAtTarget
    val accuracyRadius =
        if (metersPerDp > 0 && metersPerDp.isFinite()) (location.horizontalAccuracy / metersPerDp).toFloat().dp else 0.dp
    CircleLayer(
        id = "vialix-puck-accuracy",
        source = source,
        visible = accuracyRadius > PUCK_DOT_RADIUS + PUCK_RING_WIDTH,
        radius = const(accuracyRadius),
        color = const(color.copy(alpha = 0.12f)),
        strokeColor = const(color.copy(alpha = 0.4f)),
        strokeWidth = const(1.dp),
        pitchAlignment = const(CirclePitchAlignment.Map),
    )

    // A ~60° wedge fading away from the user, pointing along the course. Only when a course exists:
    // a stationary GPS reports none, and a cone pointing nowhere is worse than no cone.
    if (bearingDegrees != null) {
        SymbolLayer(
            id = "vialix-puck-heading",
            source = source,
            iconImage = image(rememberHeadingConePainter(color)),
            iconAnchor = const(SymbolAnchor.Bottom),
            iconRotate = const(bearingDegrees),
            iconRotationAlignment = const(IconRotationAlignment.Map),
            iconPitchAlignment = const(IconPitchAlignment.Map),
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
        )
    }

    CircleLayer(
        id = "vialix-puck-dot",
        source = source,
        radius = const(PUCK_DOT_RADIUS),
        color = const(color),
        strokeColor = const(paint.locationRing),
        strokeWidth = const(PUCK_RING_WIDTH),
        pitchAlignment = const(CirclePitchAlignment.Map),
    )
}

/** 12 dp dot ... */
private val PUCK_DOT_RADIUS = 6.dp

/** ... with a 2.5 dp white ring. */
private val PUCK_RING_WIDTH = 2.5.dp

/** How far the heading cone reaches from the dot. */
private val HEADING_CONE_RADIUS = 48.dp
private const val HEADING_CONE_HALF_ANGLE_DEG = 30.0

/**
 * The wedge has its apex at the bottom centre of the image, so anchoring the symbol at
 * [SymbolAnchor.Bottom] puts the apex on the user and rotation happens around it. Width equals
 * `2 * r * sin(30°) = r`, hence the square image.
 */
@Composable
private fun rememberHeadingConePainter(color: Color): VectorPainter = rememberVectorPainter(
    defaultWidth = HEADING_CONE_RADIUS,
    defaultHeight = HEADING_CONE_RADIUS,
    autoMirror = false,
) { viewportWidth, viewportHeight ->
    val radius = viewportHeight
    val chordY = viewportHeight - (radius * cos(Math.toRadians(HEADING_CONE_HALF_ANGLE_DEG))).toFloat()
    Path(
        pathData =
            PathData {
                moveTo(viewportWidth / 2f, viewportHeight)
                lineTo(0f, chordY)
                arcTo(radius, radius, 0f, isMoreThanHalf = false, isPositiveArc = true, viewportWidth, chordY)
                close()
            },
        fill = Brush.verticalGradient(0f to color.copy(alpha = 0f), 1f to color.copy(alpha = 0.35f)),
    )
}

/** Red dot marking the destination the user long-pressed. */
@Composable
@MaplibreComposable
fun DroppedPinLayer(pin: GeographicCoordinate?, paint: MapPaint) {
    if (pin == null) return

    val source = rememberGeoJsonSource(GeoJsonData.JsonString(pointFeatureCollectionJson(pin)))

    CircleLayer(
        id = "vialix-destination-pin",
        source = source,
        color = const(paint.pin),
        radius = const(9.dp),
        strokeColor = const(paint.pinRing),
        strokeWidth = const(2.dp),
    )
}

/**
 * The saved Home and Work places while browsing: a filled circle with the white house / briefcase
 * glyph on top, drawn by hand like the puck and the pin because the basemap has no marker sprite.
 * Tapping a marker reports its [FavoriteKind]; the map does the hit test through the circle layer's
 * `onClick`, and consuming the click keeps it from reaching Ferrostar's map-level handlers. The
 * favorite at [selected] (the current destination) is skipped so the red pin is not drawn on top of
 * it — compared by coordinate, since a destination's label can change after the pick. Not drawn
 * during guidance, like the other app layers.
 */
@Composable
@MaplibreComposable
fun SavedPlacesLayer(places: SavedPlaces, selected: GeographicCoordinate?, paint: MapPaint, onTap: (FavoriteKind) -> Unit) {
    // Layer ids must stay stable per kind, hence one pair per enum entry rather than per set place.
    FavoriteKind.entries.forEach { kind ->
        val place = places.favorite(kind) ?: return@forEach
        if (place.coordinate == selected) return@forEach
        val id = "vialix-favorite-${kind.name.lowercase()}"

        val source = rememberGeoJsonSource(GeoJsonData.JsonString(pointFeatureCollectionJson(place.coordinate)))

        CircleLayer(
            id = id,
            source = source,
            radius = const(FAVORITE_RADIUS),
            color = const(paint.favorite),
            strokeColor = const(paint.pinRing),
            strokeWidth = const(FAVORITE_RING_WIDTH),
            onClick = {
                onTap(kind)
                ClickResult.Consume
            },
        )
        // No click handler here: a tap on the glyph falls through to the circle underneath. The
        // colour filter makes the glyph white whatever the drawable's own tint attribute resolves to.
        SymbolLayer(
            id = "$id-icon",
            source = source,
            iconImage =
                image(
                    painterResource(kind.iconRes()),
                    size = DpSize(FAVORITE_GLYPH_SIZE, FAVORITE_GLYPH_SIZE),
                    colorFilter = ColorFilter.tint(paint.pinRing),
                ),
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
        )
    }
}

private val FAVORITE_RADIUS = 14.dp
private val FAVORITE_RING_WIDTH = 2.dp
private val FAVORITE_GLYPH_SIZE = 16.dp

/**
 * The proposed routes, drawn while the user decides whether to start: the alternatives in a faint
 * grey underneath, the selected one on top. Ferrostar draws the active route itself once navigation
 * begins, so this is only shown while idle. Layer ids must stay stable per slot, hence the index.
 */
@Composable
@MaplibreComposable
fun RoutePreviewLayer(routes: List<Route>, selectedIndex: Int, paint: MapPaint) {
    routes.forEachIndexed { index, route ->
        if (index != selectedIndex) {
            BorderedPolyline(
                points = route.geometry,
                idPrefix = "vialix-route-alternative-$index",
                color = paint.alternativeRoute,
                opacity = 0.85f,
                lineWidth = 6f,
                borderWidth = 1f,
            )
        }
    }
    val selected = routes.getOrNull(selectedIndex) ?: return
    BorderedPolyline(
        points = selected.geometry,
        idPrefix = "vialix-route-preview",
        color = paint.selectedRoute,
        opacity = 0.9f,
        lineWidth = 8f,
        borderWidth = 2f,
    )
}

internal fun pointFeatureCollectionJson(point: GeographicCoordinate): String =
    """{"type":"FeatureCollection","features":[{"type":"Feature","geometry":{"type":"Point","coordinates":[${point.lng},${point.lat}]},"properties":{}}]}"""
