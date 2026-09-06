// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.settings

import android.app.UiModeManager
import android.content.Context
import android.os.Build

/**
 * Tells the system the app's night mode (API 31+), which it persists per app until the app's data
 * is cleared. The launch window, `values-night` resources and `isSystemInDarkTheme()` then all
 * follow the Appearance setting from the next cold start, so a chosen theme does not flash the
 * system's before Compose draws. API 29–30 have no per-app night mode; there the pre-Compose frame
 * follows the system and may briefly differ from the chosen theme.
 */
fun applyAppNightMode(context: Context, mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val uiModeManager = context.getSystemService(UiModeManager::class.java) ?: return
    uiModeManager.setApplicationNightMode(
        when (mode) {
            // For the per-app mode the framework maps CUSTOM (and AUTO) to "undefined", i.e.
            // follow the system's night mode; NO and YES pin it.
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_CUSTOM

            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO

            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        },
    )
}
