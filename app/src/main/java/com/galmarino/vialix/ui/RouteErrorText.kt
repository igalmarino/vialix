// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.galmarino.vialix.R
import com.galmarino.vialix.navigation.RouteError

/** The user-facing sentence for a [RouteError]; shared by the preview sheet and the snackbar. */
@Composable
internal fun errorText(error: RouteError): String = when (error) {
    RouteError.NoLocationFix -> stringResource(R.string.error_no_fix_yet)
    RouteError.NoRouteFound -> stringResource(R.string.error_no_route)
    is RouteError.RequestFailed -> failureText(error.failure, R.string.error_route_server, R.string.error_route_request)
}
