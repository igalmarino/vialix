// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

// Named after the topic (the chrome over the map), not the one class in it.
@file:Suppress("ktlint:standard:filename")

package com.galmarino.vialix.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.ui.theme.LocalDarkTheme
import com.galmarino.vialix.ui.theme.LocationBlue
import com.galmarino.vialix.ui.theme.LocationBlueDark

/**
 * Colours of the chrome drawn over the map (search pill, status-bar scrim, FAB stack). White over
 * the light basemap, near-black over the dark one; keyed on the resolved dark flag rather than the
 * Material colour scheme because the chrome has to read against the tiles, which are never
 * wallpaper-tinted. [accent] marks an active control and matches the location puck.
 */
internal data class MapChromeColors(val surface: Color, val content: Color, val hint: Color, val outline: Color, val accent: Color) {
    val outlineStroke: BorderStroke
        get() = BorderStroke(0.5.dp, outline)
}

/** Pure, so it can be unit-tested; [mapChrome] reads the flag from the composition. */
internal fun mapChromeColors(dark: Boolean): MapChromeColors = if (dark) DarkMapChrome else LightMapChrome

@Composable internal fun mapChrome(): MapChromeColors = mapChromeColors(LocalDarkTheme.current)

private val LightMapChrome =
    MapChromeColors(
        surface = Color.White,
        content = Color(0xFF1F1F1F),
        hint = Color(0xFF5F6368),
        outline = Color.Black.copy(alpha = 0.18f),
        accent = LocationBlue,
    )

/** A step lighter than the night style's ground (about `#1e1b18`) so the pill reads as a card. */
private val DarkMapChrome =
    MapChromeColors(
        surface = Color(0xFF2A2B2E),
        content = Color(0xFFE6E6E6),
        hint = Color(0xFFA9ACB1),
        outline = Color.White.copy(alpha = 0.14f),
        accent = LocationBlueDark,
    )
