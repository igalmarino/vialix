package com.galmarino.vialix.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * The static Material 3 colour schemes, used below Android 12 (no dynamic colour) or when
 * `VialixTheme(dynamicColor = false)`. They are full tonal palettes seeded on [LocationBlue]
 * (HCT hue 266, and the seed's own chroma for the primary palette so the brand blue survives),
 * with the standard Material 3 tone assignments per role. Overriding `primary` alone would leave
 * `primaryContainer`, `inversePrimary`, `secondary` and friends at the baseline purple.
 *
 * The tones were generated with material3's HCT solver (`HctSolver.solveToInt`) from the seed
 * `#1F5FBF`; regenerate all of them together if the seed changes.
 */

/** The blue of everything location-related, on the light basemap; also the light theme's primary. */
val LocationBlue = Color(0xFF1F5FBF)

/** Its counterpart on the dark basemap; also the dark theme's primary without dynamic colour. */
val LocationBlueDark = Color(0xFF9DC1FF)

internal val LightColors =
    lightColorScheme(
        primary = LocationBlue,
        onPrimary = PrimaryTones.T100,
        primaryContainer = PrimaryTones.T90,
        onPrimaryContainer = PrimaryTones.T10,
        inversePrimary = PrimaryTones.T80,
        secondary = SecondaryTones.T40,
        onSecondary = SecondaryTones.T100,
        secondaryContainer = SecondaryTones.T90,
        onSecondaryContainer = SecondaryTones.T10,
        tertiary = TertiaryTones.T40,
        onTertiary = TertiaryTones.T100,
        tertiaryContainer = TertiaryTones.T90,
        onTertiaryContainer = TertiaryTones.T10,
        error = ErrorTones.T40,
        onError = ErrorTones.T100,
        errorContainer = ErrorTones.T90,
        onErrorContainer = ErrorTones.T10,
        background = NeutralTones.T98,
        onBackground = NeutralTones.T10,
        surface = NeutralTones.T98,
        onSurface = NeutralTones.T10,
        surfaceVariant = NeutralVariantTones.T90,
        onSurfaceVariant = NeutralVariantTones.T30,
        surfaceTint = LocationBlue,
        inverseSurface = NeutralTones.T20,
        inverseOnSurface = NeutralTones.T95,
        outline = NeutralVariantTones.T50,
        outlineVariant = NeutralVariantTones.T80,
        scrim = NeutralTones.T0,
        surfaceBright = NeutralTones.T98,
        surfaceDim = NeutralTones.T87,
        surfaceContainer = NeutralTones.T94,
        surfaceContainerHigh = NeutralTones.T92,
        surfaceContainerHighest = NeutralTones.T90,
        surfaceContainerLow = NeutralTones.T96,
        surfaceContainerLowest = NeutralTones.T100,
    )

internal val DarkColors =
    darkColorScheme(
        primary = LocationBlueDark,
        onPrimary = PrimaryTones.T20,
        primaryContainer = PrimaryTones.T30,
        onPrimaryContainer = PrimaryTones.T90,
        inversePrimary = PrimaryTones.T40,
        secondary = SecondaryTones.T80,
        onSecondary = SecondaryTones.T20,
        secondaryContainer = SecondaryTones.T30,
        onSecondaryContainer = SecondaryTones.T90,
        tertiary = TertiaryTones.T80,
        onTertiary = TertiaryTones.T20,
        tertiaryContainer = TertiaryTones.T30,
        onTertiaryContainer = TertiaryTones.T90,
        error = ErrorTones.T80,
        onError = ErrorTones.T20,
        errorContainer = ErrorTones.T30,
        onErrorContainer = ErrorTones.T90,
        background = NeutralTones.T6,
        onBackground = NeutralTones.T90,
        surface = NeutralTones.T6,
        onSurface = NeutralTones.T90,
        surfaceVariant = NeutralVariantTones.T30,
        onSurfaceVariant = NeutralVariantTones.T80,
        surfaceTint = LocationBlueDark,
        inverseSurface = NeutralTones.T90,
        inverseOnSurface = NeutralTones.T20,
        outline = NeutralVariantTones.T60,
        outlineVariant = NeutralVariantTones.T30,
        scrim = NeutralTones.T0,
        surfaceBright = NeutralTones.T24,
        surfaceDim = NeutralTones.T6,
        surfaceContainer = NeutralTones.T12,
        surfaceContainerHigh = NeutralTones.T17,
        surfaceContainerHighest = NeutralTones.T22,
        surfaceContainerLow = NeutralTones.T10,
        surfaceContainerLowest = NeutralTones.T4,
    )

/** Primary tonal palette (HCT hue 266, chroma 57). */
internal object PrimaryTones {
    val T0 = Color(0xFF000000)
    val T4 = Color(0xFF000D28)
    val T6 = Color(0xFF001232)
    val T10 = Color(0xFF001A42)
    val T12 = Color(0xFF001E49)
    val T17 = Color(0xFF00285D)
    val T20 = Color(0xFF002E6A)
    val T22 = Color(0xFF003272)
    val T24 = Color(0xFF00367B)
    val T30 = Color(0xFF004395)
    val T40 = Color(0xFF185BBB)
    val T50 = Color(0xFF3C75D6)
    val T60 = Color(0xFF5A8FF2)
    val T70 = Color(0xFF81AAFF)
    val T80 = Color(0xFFAEC6FF)
    val T87 = Color(0xFFCBDAFF)
    val T90 = Color(0xFFD8E2FF)
    val T92 = Color(0xFFE0E8FF)
    val T94 = Color(0xFFE9EDFF)
    val T95 = Color(0xFFEDF0FF)
    val T96 = Color(0xFFF1F3FF)
    val T98 = Color(0xFFF9F9FF)
    val T100 = Color(0xFFFFFFFF)
}

/** Secondary tonal palette (HCT hue 266, chroma 16). */
internal object SecondaryTones {
    val T0 = Color(0xFF000000)
    val T4 = Color(0xFF060E1E)
    val T6 = Color(0xFF0B1323)
    val T10 = Color(0xFF141B2C)
    val T12 = Color(0xFF181F30)
    val T17 = Color(0xFF222A3B)
    val T20 = Color(0xFF293041)
    val T22 = Color(0xFF2D3546)
    val T24 = Color(0xFF31394B)
    val T30 = Color(0xFF3F4759)
    val T40 = Color(0xFF575E71)
    val T50 = Color(0xFF6F778B)
    val T60 = Color(0xFF8990A5)
    val T70 = Color(0xFFA3ABC0)
    val T80 = Color(0xFFBFC6DC)
    val T87 = Color(0xFFD2DAF0)
    val T90 = Color(0xFFDBE2F9)
    val T92 = Color(0xFFE1E8FF)
    val T94 = Color(0xFFE9EDFF)
    val T95 = Color(0xFFEDF0FF)
    val T96 = Color(0xFFF1F3FF)
    val T98 = Color(0xFFF9F9FF)
    val T100 = Color(0xFFFFFFFF)
}

/** Tertiary tonal palette (HCT hue 326, chroma 24). */
internal object TertiaryTones {
    val T0 = Color(0xFF000000)
    val T4 = Color(0xFF1B051F)
    val T6 = Color(0xFF200A24)
    val T10 = Color(0xFF29132D)
    val T12 = Color(0xFF2E1731)
    val T17 = Color(0xFF39213C)
    val T20 = Color(0xFF402843)
    val T22 = Color(0xFF452C48)
    val T24 = Color(0xFF49304C)
    val T30 = Color(0xFF583E5B)
    val T40 = Color(0xFF715573)
    val T50 = Color(0xFF8B6D8D)
    val T60 = Color(0xFFA687A7)
    val T70 = Color(0xFFC2A1C3)
    val T80 = Color(0xFFDEBCDF)
    val T87 = Color(0xFFF3CFF3)
    val T90 = Color(0xFFFCD7FB)
    val T92 = Color(0xFFFFDEFE)
    val T94 = Color(0xFFFFE7FD)
    val T95 = Color(0xFFFFEBFC)
    val T96 = Color(0xFFFFEFFB)
    val T98 = Color(0xFFFFF7FA)
    val T100 = Color(0xFFFFFFFF)
}

/** Neutral tonal palette (HCT hue 266, chroma 6). */
internal object NeutralTones {
    val T0 = Color(0xFF000000)
    val T4 = Color(0xFF0C0E13)
    val T6 = Color(0xFF111318)
    val T10 = Color(0xFF1A1B20)
    val T12 = Color(0xFF1E1F25)
    val T17 = Color(0xFF282A2F)
    val T20 = Color(0xFF2F3036)
    val T22 = Color(0xFF33353A)
    val T24 = Color(0xFF37393E)
    val T30 = Color(0xFF45474C)
    val T40 = Color(0xFF5D5E64)
    val T50 = Color(0xFF76777D)
    val T60 = Color(0xFF909097)
    val T70 = Color(0xFFAAABB1)
    val T80 = Color(0xFFC6C6CD)
    val T87 = Color(0xFFD9D9E0)
    val T90 = Color(0xFFE2E2E9)
    val T92 = Color(0xFFE8E7EF)
    val T94 = Color(0xFFEEEDF4)
    val T95 = Color(0xFFF0F0F7)
    val T96 = Color(0xFFF3F3FA)
    val T98 = Color(0xFFF9F9FF)
    val T100 = Color(0xFFFFFFFF)
}

/** NeutralVariant tonal palette (HCT hue 266, chroma 8). */
internal object NeutralVariantTones {
    val T0 = Color(0xFF000000)
    val T4 = Color(0xFF0B0E15)
    val T6 = Color(0xFF10131A)
    val T10 = Color(0xFF191B22)
    val T12 = Color(0xFF1D2027)
    val T17 = Color(0xFF272A31)
    val T20 = Color(0xFF2E3038)
    val T22 = Color(0xFF32353C)
    val T24 = Color(0xFF363941)
    val T30 = Color(0xFF44474F)
    val T40 = Color(0xFF5C5E66)
    val T50 = Color(0xFF75777F)
    val T60 = Color(0xFF8E9099)
    val T70 = Color(0xFFA9ABB4)
    val T80 = Color(0xFFC4C6D0)
    val T87 = Color(0xFFD8D9E3)
    val T90 = Color(0xFFE1E2EC)
    val T92 = Color(0xFFE6E7F2)
    val T94 = Color(0xFFECEDF7)
    val T95 = Color(0xFFEFF0FA)
    val T96 = Color(0xFFF2F3FD)
    val T98 = Color(0xFFF9F9FF)
    val T100 = Color(0xFFFFFFFF)
}

/** Error tonal palette (HCT hue 25, chroma 84). */
internal object ErrorTones {
    val T0 = Color(0xFF000000)
    val T4 = Color(0xFF280001)
    val T6 = Color(0xFF310001)
    val T10 = Color(0xFF410002)
    val T12 = Color(0xFF490002)
    val T17 = Color(0xFF5C0004)
    val T20 = Color(0xFF690005)
    val T22 = Color(0xFF710005)
    val T24 = Color(0xFF790006)
    val T30 = Color(0xFF93000A)
    val T40 = Color(0xFFBA1A1A)
    val T50 = Color(0xFFDE3730)
    val T60 = Color(0xFFFF5449)
    val T70 = Color(0xFFFF897D)
    val T80 = Color(0xFFFFB4AB)
    val T87 = Color(0xFFFFCFC9)
    val T90 = Color(0xFFFFDAD6)
    val T92 = Color(0xFFFFE2DE)
    val T94 = Color(0xFFFFE9E6)
    val T95 = Color(0xFFFFEDEA)
    val T96 = Color(0xFFFFF0EE)
    val T98 = Color(0xFFFFF8F7)
    val T100 = Color(0xFFFFFFFF)
}
