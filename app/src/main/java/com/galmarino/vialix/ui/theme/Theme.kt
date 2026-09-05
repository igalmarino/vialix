package com.galmarino.vialix.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the app is drawn dark: the Appearance setting resolved against the system, computed
 * once by `MainActivity` and handed to [VialixTheme]. The chrome over the map and the map-layer
 * paint key on this rather than on `MaterialTheme.colorScheme`, whose tones move with the
 * wallpaper under dynamic colour while the basemap is exactly one of two styles.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun VialixTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            // minSdk is 29, but dynamic color needs Android 12 (API 31). Guarded at runtime.
            dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            // Full tonal palettes seeded on the location blue, see Color.kt.
            darkTheme -> DarkColors
            else -> LightColors
        }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
