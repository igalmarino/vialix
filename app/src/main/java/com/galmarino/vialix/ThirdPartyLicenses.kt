// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix

/**
 * One shipped third-party component: what it is called in the licences screen, its licence as
 * declared in its POM (SPDX identifier where one exists), where it lives, and which Maven groups
 * it accounts for (a unit test checks that every runtime dependency in the version catalog is
 * covered by some entry, so a new dependency cannot be shipped without being credited).
 */
data class ThirdPartyComponent(
    val name: String,
    val license: String,
    val url: String,
    val groupPrefixes: List<String>,
    /** Only in the `full` flavour. */
    val fullFlavourOnly: Boolean = false,
)

/** Maintained by hand from the resolved dependency POMs; see [ThirdPartyComponent]. */
object ThirdPartyLicenses {
    val components: List<ThirdPartyComponent> =
        listOf(
            ThirdPartyComponent("Ferrostar", "BSD-3-Clause", "https://github.com/stadiamaps/ferrostar", listOf("com.stadiamaps.ferrostar")),
            ThirdPartyComponent(
                "MapLibre Compose",
                "BSD-3-Clause",
                "https://github.com/maplibre/maplibre-compose",
                listOf("org.maplibre.compose"),
            ),
            ThirdPartyComponent(
                "MapLibre Native",
                "BSD-2-Clause",
                "https://github.com/maplibre/maplibre-native",
                listOf("org.maplibre.gl"),
            ),
            ThirdPartyComponent("spatialK", "MIT", "https://github.com/maplibre/spatialk", listOf("org.maplibre.spatialk")),
            ThirdPartyComponent(
                "OkHttp and Okio",
                "Apache-2.0",
                "https://square.github.io/okhttp/",
                listOf("com.squareup.okhttp3", "com.squareup.okio"),
            ),
            ThirdPartyComponent(
                "Java Native Access (JNA)",
                "LGPL-2.1-or-later or Apache-2.0 (dual-licensed)",
                "https://github.com/java-native-access/jna",
                listOf("net.java.dev.jna"),
            ),
            ThirdPartyComponent(
                "AndroidX: Jetpack Compose, Material 3, Lifecycle, Core, Activity",
                "Apache-2.0",
                "https://developer.android.com/jetpack/androidx",
                listOf("androidx."),
            ),
            ThirdPartyComponent(
                "Kotlin standard library, kotlinx.coroutines, kotlinx.serialization",
                "Apache-2.0",
                "https://kotlinlang.org",
                listOf("org.jetbrains.kotlin", "org.jetbrains.kotlinx"),
            ),
            ThirdPartyComponent(
                "desugar_jdk_libs",
                "GPL-2.0-only with Classpath-exception-2.0",
                "https://github.com/google/desugar_jdk_libs",
                listOf("com.android.tools"),
            ),
            ThirdPartyComponent(
                "Material Symbols (icon shapes)",
                "Apache-2.0",
                "https://fonts.google.com/icons",
                emptyList(),
            ),
            ThirdPartyComponent(
                "Google Play services location",
                "Android Software Development Kit License",
                "https://developers.google.com/android/guides/overview",
                listOf("com.google.android.gms"),
                fullFlavourOnly = true,
            ),
        )
}
