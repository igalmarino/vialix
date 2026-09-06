// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.galmarino.vialix.R

/**
 * One full-width pill along the top of the map. Looks like a search field, acts as a button
 * (tapping anywhere but the icons opens the full-screen search); the leading icon opens the menu
 * drawer, the trailing one starts voice search and is omitted when [onMicClick] is null (no speech
 * recogniser on the device). Painted in the [mapChrome] colours, not the theme's, so it reads
 * against the basemap.
 */
@Composable
fun TopSearchBar(onMenuClick: () -> Unit, onSearchClick: () -> Unit, onMicClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val chrome = mapChrome()
    Surface(
        onClick = onSearchClick,
        shape = CircleShape,
        color = chrome.surface,
        contentColor = chrome.content,
        border = chrome.outlineStroke,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenuClick) {
                Icon(painter = painterResource(R.drawable.ic_menu), contentDescription = stringResource(R.string.menu))
            }
            // The visible hint mentions the long-press; a screen reader gets the action instead.
            val accessibleName = stringResource(R.string.search_placeholder)
            Text(
                text = stringResource(R.string.search_hint),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp).semantics { contentDescription = accessibleName },
                style = MaterialTheme.typography.bodyLarge,
                color = chrome.hint,
                maxLines = 1,
            )
            if (onMicClick != null) {
                IconButton(onClick = onMicClick) {
                    Icon(painter = painterResource(R.drawable.ic_mic), contentDescription = stringResource(R.string.voice_search))
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

/** Keeps status-bar glyphs legible over the map tiles; fades into the map below the bar. */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier) {
    val chrome = mapChrome()
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(statusBarHeight + STATUS_BAR_SCRIM_FADE)
                .background(Brush.verticalGradient(listOf(chrome.surface.copy(alpha = 0.65f), Color.Transparent))),
    )
}

/** How far below the status bar the scrim fades out. */
private val STATUS_BAR_SCRIM_FADE = 20.dp
