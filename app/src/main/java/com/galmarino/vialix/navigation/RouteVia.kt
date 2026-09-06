// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import uniffi.ferrostar.Route

/**
 * The road that best characterises a route ("via A1"): the name of its longest named step. Used to
 * tell alternatives apart in the preview; null when no step is named.
 */
fun Route.viaName(): String? = steps.filter { !it.roadName.isNullOrBlank() }.maxByOrNull { it.distance }?.roadName
