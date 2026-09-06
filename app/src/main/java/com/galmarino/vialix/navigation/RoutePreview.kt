// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import uniffi.ferrostar.Route

/**
 * The route preview as one value: nothing, a request in flight, a set of routes with one of them
 * selected, or a failure. A sum type rather than four independent fields, so that "fetching and
 * failed at once" or "a selection without routes" cannot be represented, and so the transitions
 * ([select], [of]) can be tested without Android.
 */
sealed interface RoutePreview {
    /** No destination, or the preview was dropped. */
    data object None : RoutePreview

    data object Fetching : RoutePreview

    /** Valhalla's preferred route first, then its alternatives; [selected] is the one Start refers to. */
    data class Ready(val options: List<Route>, val selected: Int = 0) : RoutePreview {
        init {
            require(options.isNotEmpty()) { "A ready preview has at least one route" }
            require(selected in options.indices) { "Selected route $selected of ${options.size}" }
        }

        override val route: Route
            get() = options[selected]
    }

    data class Failed(val error: RouteError) : RoutePreview

    /** Every fetched route, or none. */
    val routeOptions: List<Route>
        get() = (this as? Ready)?.options ?: emptyList()

    /** Index into [routeOptions] of the selected route; 0 when there is none. */
    val selectedIndex: Int
        get() = (this as? Ready)?.selected ?: 0

    /** The route Start refers to, if there is one. */
    val route: Route?
        get() = (this as? Ready)?.route

    /** The tapped alternative becomes the selection; out of range, or without routes, nothing changes. */
    fun select(index: Int): RoutePreview = if (this is Ready && index in options.indices) copy(selected = index) else this

    companion object {
        /** The server's answer: its first route selected, or [RouteError.NoRouteFound] for an empty one. */
        fun of(routes: List<Route>): RoutePreview = if (routes.isEmpty()) Failed(RouteError.NoRouteFound) else Ready(routes)
    }
}
