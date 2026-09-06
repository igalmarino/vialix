import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional developer overrides live in local.properties (git-ignored) or in the
// environment (VIALIX_VALHALLA_ENDPOINT, ...). Anything left empty falls back to the
// defaults in NavConfig.kt.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun override(name: String): String =
    localProperties.getProperty(name)
        ?: System.getenv("VIALIX_" + name.replace(Regex("([A-Z])"), "_$1").uppercase())
        ?: ""

/** The value as a Java string literal, so a quote or backslash in an override cannot break BuildConfig. */
fun String.asJavaLiteral(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Release signing: keystore.properties (git-ignored; see keystore.properties.example) or, in CI,
// the VIALIX_STORE_FILE / VIALIX_STORE_PASSWORD / VIALIX_KEY_ALIAS / VIALIX_KEY_PASSWORD
// environment. With neither, assembleRelease produces an unsigned APK, which is what the CI
// build on every push wants.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signing(name: String): String? =
    (keystoreProperties.getProperty(name) ?: System.getenv("VIALIX_" + name.replace(Regex("([A-Z])"), "_$1").uppercase()))
        ?.takeIf { it.isNotBlank() }

val releaseStoreFile = signing("storeFile")?.let { rootProject.file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.galmarino.vialix"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.galmarino.vialix"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "VALHALLA_ENDPOINT", override("valhallaEndpoint").asJavaLiteral())
        buildConfigField("String", "ROUTING_PROFILE", override("routingProfile").asJavaLiteral())
        buildConfigField("String", "MAP_STYLE_URL", override("mapStyleUrl").asJavaLiteral())
        buildConfigField("String", "MAP_STYLE_URL_DARK", override("mapStyleUrlDark").asJavaLiteral())
        buildConfigField("String", "CLIENT_ID", override("clientId").asJavaLiteral())
        buildConfigField("String", "VOICE_GUIDANCE", override("voiceGuidance").asJavaLiteral())
        buildConfigField("String", "GEOCODER_ENDPOINT", override("geocoderEndpoint").asJavaLiteral())

        ndk {
            // Ferrostar and MapLibre ship native code for every ABI (~18 MB each). Keep the two
            // ARM targets phones use plus x86_64 for the emulator; drop 32-bit x86 and the mips /
            // armeabi stubs JNA carries.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // `full` adds Google Play's fused location provider, the one proprietary dependency; `foss`
    // runs entirely on open-source components and uses the platform LocationManager. Same
    // applicationId: F-Droid builds `foss`, everything else `full`. `location/FusedLocationProvider`
    // exists once per flavour (src/full, src/foss) with the same `create()` signature.
    flavorDimensions += "distribution"
    productFlavors {
        create("full") {
            dimension = "distribution"
            isDefault = true
        }
        create("foss") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = signing("storePassword")
                keyAlias = signing("keyAlias")
                keyPassword = signing("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // A debug install can sit next to the release one on the same device.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // R8 with Ferrostar's consumer rules (JNA + uniffi bindings) plus proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    dependenciesInfo {
        // No Play-encrypted dependency metadata in the APK: reproducible builds (F-Droid) need it off.
        includeInApk = false
        includeInBundle = false
    }

    lint {
        lintConfig = file("lint.xml")
        // Timber is not a dependency; the stock check fires on every android.util.Log call.
        disable += "LogNotTimber"
        // Dependency updates are Dependabot's job (.github/dependabot.yml), not a lint finding.
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    bundle {
        // The in-app language switcher needs every translation installed; an app bundle must not
        // split resources by language (Lint AppBundleLocaleChanges).
        language { enableSplit = false }
    }

    androidResources {
        // Emits android:localeConfig from the values-* folders (plus res/resources.properties for
        // the unqualified one), so Settings > Apps > Vialix > Language offers exactly the shipped
        // translations. Keep UiLanguage.SUPPORTED_TAGS in step; a unit test checks the folders.
        generateLocaleConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        // android.util.Log etc. return defaults instead of throwing "Stub!" in JVM tests.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.ferrostar.core)
    implementation(libs.ferrostar.ui.compose)
    implementation(libs.ferrostar.ui.maplibre)
    implementation(libs.ferrostar.ui.formatters)
    implementation(libs.maplibre.compose)

    // Fused location on devices that have Play Services (full flavour only); the app falls back to
    // LocationManager without it, and the foss flavour never has it.
    "fullImplementation"(libs.play.services.location)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Android's org.json is a throwing stub on the JVM test classpath; this is the real thing.
    testImplementation(libs.org.json)
    testImplementation(libs.okhttp.mockwebserver)
}
