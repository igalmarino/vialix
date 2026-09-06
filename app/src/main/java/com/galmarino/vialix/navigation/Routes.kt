// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.navigation

import uniffi.ferrostar.Route

/** Total travel time in seconds; Ferrostar only exposes it per step. */
val Route.durationSeconds: Double
    get() = steps.sumOf { it.duration }
