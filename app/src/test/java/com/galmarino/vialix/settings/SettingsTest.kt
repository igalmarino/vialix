package com.galmarino.vialix.settings

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {

    @Test
    fun `the phone's measurement system preference wins over the region`() {
        // Android 14+ appends Settings > Regional preferences > Measurement system as the `ms` extension.
        assertEquals(DistanceUnits.IMPERIAL, DistanceUnits.forLocale(Locale.forLanguageTag("es-CL-u-ms-ussystem")))
        assertEquals(DistanceUnits.IMPERIAL, DistanceUnits.forLocale(Locale.forLanguageTag("de-DE-u-ms-uksystem")))
        assertEquals(DistanceUnits.METRIC, DistanceUnits.forLocale(Locale.forLanguageTag("en-US-u-ms-metric")))
        // An unknown value or no preference falls back to the region.
        assertEquals(DistanceUnits.METRIC, DistanceUnits.forLocale(Locale.forLanguageTag("es-CL-u-ms-bogus")))
        assertEquals(DistanceUnits.IMPERIAL, DistanceUnits.forLocale(Locale.forLanguageTag("en-US-u-ms-bogus")))
        assertEquals(DistanceUnits.IMPERIAL, DistanceUnits.forLocale(Locale.forLanguageTag("en-US-u-ca-gregory")))
    }

    @Test
    fun `miles countries default to imperial units`() {
        assertEquals(DistanceUnits.IMPERIAL, DistanceUnits.forLocale(Locale.US))
        assertEquals(DistanceUnits.IMPERIAL, DistanceUnits.forLocale(Locale.UK))
    }

    @Test
    fun `everywhere else defaults to metric`() {
        assertEquals(DistanceUnits.METRIC, DistanceUnits.forLocale(Locale.forLanguageTag("es-AR")))
        assertEquals(DistanceUnits.METRIC, DistanceUnits.forLocale(Locale.GERMANY))
        assertEquals(DistanceUnits.METRIC, DistanceUnits.forLocale(Locale.ROOT))
    }

    @Test
    fun `no language choice follows the device locale`() {
        val settings = Settings(languageTag = null)
        assertEquals("es-ES", settings.resolvedLanguageTag(Locale.forLanguageTag("es-AR")))
        assertEquals("en-US", settings.resolvedLanguageTag(Locale.forLanguageTag("xx-XX")))
    }

    @Test
    fun `an explicit language choice wins over the device locale`() {
        val settings = Settings(languageTag = "fr-FR")
        assertEquals("fr-FR", settings.resolvedLanguageTag(Locale.forLanguageTag("es-AR")))
    }

    @Test
    fun `system theme mode follows the system`() {
        assertTrue(Settings(themeMode = ThemeMode.SYSTEM).resolvesToDark(systemDark = true))
        assertFalse(Settings(themeMode = ThemeMode.SYSTEM).resolvesToDark(systemDark = false))
    }

    @Test
    fun `explicit theme modes ignore the system`() {
        assertFalse(Settings(themeMode = ThemeMode.LIGHT).resolvesToDark(systemDark = true))
        assertTrue(Settings(themeMode = ThemeMode.DARK).resolvesToDark(systemDark = false))
    }

    @Test
    fun `default theme mode is system`() {
        assertEquals(ThemeMode.SYSTEM, Settings().themeMode)
    }

    @Test
    fun `default app language follows the device`() {
        assertEquals(null, Settings().uiLanguageTag)
    }

    @Test
    fun `valhalla unit names`() {
        assertEquals("kilometers", DistanceUnits.METRIC.valhallaUnits)
        assertEquals("miles", DistanceUnits.IMPERIAL.valhallaUnits)
    }
}
