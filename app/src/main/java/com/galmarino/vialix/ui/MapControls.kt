// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.R
import com.galmarino.vialix.navigation.CameraFollowMode
import com.stadiamaps.ferrostar.maplibreui.runtime.NavigationCameraMode

/** The Ferrostar camera mode that realises a [CameraFollowMode] while browsing. */
fun CameraFollowMode.toCameraMode(): NavigationCameraMode = when (this) {
    CameraFollowMode.FREE -> NavigationCameraMode.FREE
    CameraFollowMode.FOLLOW -> NavigationCameraMode.FOLLOW_USER
    CameraFollowMode.HEADING -> NavigationCameraMode.FOLLOW_USER_WITH_BEARING
}

/**
 * What the button shows for the camera's current mode. Ferrostar switches to `FREE` itself on any
 * gesture and to `OVERVIEW` when framing a route; both mean "not following" to the user.
 */
fun NavigationCameraMode.toFollowMode(): CameraFollowMode = when (this) {
    NavigationCameraMode.FOLLOW_USER -> CameraFollowMode.FOLLOW
    NavigationCameraMode.FOLLOW_USER_WITH_BEARING -> CameraFollowMode.HEADING
    NavigationCameraMode.OVERVIEW, NavigationCameraMode.FREE -> CameraFollowMode.FREE
}

/**
 * The round buttons at the bottom-right of the map, currently just my-location (56 dp); a layers
 * button will join it once there are alternative styles to offer. The caller anchors the stack to
 * the top edge of the bottom sheet.
 */
@Composable
fun MapFabStack(followMode: CameraFollowMode, onMyLocationClick: () -> Unit, modifier: Modifier = Modifier) {
    val chrome = mapChrome()
    Column(modifier = modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MapControlButton(
            chrome = chrome,
            size = 56.dp,
            iconRes =
                when (followMode) {
                    CameraFollowMode.FREE -> R.drawable.ic_location_searching
                    CameraFollowMode.FOLLOW -> R.drawable.ic_my_location
                    CameraFollowMode.HEADING -> R.drawable.ic_navigation
                },
            contentDescription =
                stringResource(
                    when (followMode) {
                        CameraFollowMode.FREE -> R.string.my_location_free
                        CameraFollowMode.FOLLOW -> R.string.my_location_follow
                        CameraFollowMode.HEADING -> R.string.my_location_heading
                    },
                ),
            // The chrome accent, not the theme's primary: on the theme-independent disc the
            // latter can be light-on-light in dark mode, and this way the button matches the puck.
            tint = if (followMode.isFollowing) chrome.accent else chrome.content,
            onClick = onMyLocationClick,
        )
    }
}

/** Disc in the map-chrome colours with a hairline outline and a whisper of shadow; no Material FAB elevation. */
@Composable
private fun MapControlButton(
    chrome: MapChromeColors,
    size: Dp,
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = chrome.content,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = chrome.surface,
        contentColor = tint,
        border = chrome.outlineStroke,
        shadowElevation = 2.dp,
        modifier = Modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painter = painterResource(iconRes), contentDescription = contentDescription, tint = tint)
        }
    }
}
