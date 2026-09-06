// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import com.galmarino.vialix.R

/** A Valhalla costing model as offered to the user, with its label and icon. */
data class RoutingProfile(val costing: String, val labelRes: Int, val iconRes: Int)

/** The profiles offered by the Settings picker and the preview sheet's mode switcher, in display order. */
val ROUTING_PROFILES =
    listOf(
        RoutingProfile("auto", R.string.profile_car, R.drawable.ic_directions_car),
        RoutingProfile("bicycle", R.string.profile_bicycle, R.drawable.ic_directions_bike),
        RoutingProfile("pedestrian", R.string.profile_walking, R.drawable.ic_directions_walk),
    )
