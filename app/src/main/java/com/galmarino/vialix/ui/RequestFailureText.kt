// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.galmarino.vialix.R
import com.galmarino.vialix.RequestFailure

/**
 * The user-facing line for a failed server request. Offline is the same story whichever server
 * it was; the other two are worded per feature (`R.string.error_route_server`, ...).
 */
@Composable
internal fun failureText(failure: RequestFailure, @StringRes serverError: Int, @StringRes other: Int): String = when (failure) {
    RequestFailure.Offline -> stringResource(R.string.error_offline)
    is RequestFailure.ServerError -> stringResource(serverError, failure.statusCode)
    RequestFailure.Other -> stringResource(other)
}
