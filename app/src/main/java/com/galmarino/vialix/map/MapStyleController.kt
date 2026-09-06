// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.map

import com.galmarino.vialix.NavConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The basemap for the current theme, as a [MapStyleState]: patched (and recoloured for the night
 * when no dark style is configured), or the decision to load the plain URL. App-scoped, because
 * the style outlives any screen and has nothing to do with navigation.
 *
 * Nothing loads until the Activity has reported the theme ([onDarkThemeChanged]), so the first
 * (and, in the common case, only) style load is the right one. On a later theme change the map
 * keeps its current style until the new one is ready; a flip mid-download cancels the download.
 * [retry] re-runs a failed download.
 */
class MapStyleController(private val config: NavConfig, private val loader: MapStyleLoader, scope: CoroutineScope) {
    /** Whether the app is drawn dark; `null` until the Activity has said. */
    private val darkTheme = MutableStateFlow<Boolean?>(null)

    /** Bumped by [retry]; part of the key the loader re-runs on. */
    private val attempt = MutableStateFlow(0)

    private val _state = MutableStateFlow<MapStyleState>(MapStyleState.Loading)
    val state: StateFlow<MapStyleState> = _state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            combine(darkTheme.filterNotNull(), attempt) { dark, n -> dark to n }
                .distinctUntilChanged()
                .collectLatest { (dark, _) ->
                    val url = config.mapStyleUrlFor(dark)
                    _state.value =
                        withTimeoutOrNull(LOAD_TIMEOUT_MS) { loader.load(url, night = config.derivesNightStyle(dark)) }
                            ?: MapStyleState.Unavailable(url, downloadFailed = true)
                }
        }
    }

    /** The resolved theme (setting + system), pushed by the Activity. */
    fun onDarkThemeChanged(dark: Boolean) {
        darkTheme.value = dark
    }

    /** Try the download again (Retry on the "map could not be loaded" message). */
    fun retry() {
        _state.value = MapStyleState.Loading
        attempt.update { it + 1 }
    }

    private companion object {
        /** Past this, the map loads the plain style URL rather than staying blank any longer. */
        const val LOAD_TIMEOUT_MS = 3000L
    }
}
