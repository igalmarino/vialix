package com.galmarino.vialix.settings

import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLanguageTest {

    @Test
    fun `a device language with a translation gets it, whatever the region`() {
        assertEquals("es", UiLanguage.resolve(Locale.forLanguageTag("es-AR")))
        assertEquals("de", UiLanguage.resolve(Locale.forLanguageTag("de-CH")))
        assertEquals("fr", UiLanguage.resolve(Locale.CANADA_FRENCH))
        assertEquals("it", UiLanguage.resolve(Locale.ITALY))
        assertEquals("en", UiLanguage.resolve(Locale.UK))
    }

    @Test
    fun `an untranslated device language falls back to English`() {
        assertEquals("en", UiLanguage.resolve(Locale.forLanguageTag("pt-BR")))
        assertEquals("en", UiLanguage.resolve(Locale.ROOT))
    }

    @Test
    fun `english is the default and comes first`() {
        assertEquals(UiLanguage.DEFAULT_TAG, UiLanguage.SUPPORTED_TAGS.first())
        assertEquals(UiLanguage.SUPPORTED_TAGS.size, UiLanguage.SUPPORTED_TAGS.toSet().size)
    }

    /** The list and the resource folders must agree: the OS picker (generateLocaleConfig) follows the folders. */
    @Test
    fun `every offered language ships a translation`() {
        // Gradle runs unit tests with the module directory as the working directory.
        val res = File("src/main/res")
        assertTrue("run from the app module: ${res.absolutePath}", res.isDirectory)
        for (tag in UiLanguage.SUPPORTED_TAGS - UiLanguage.DEFAULT_TAG) {
            assertTrue("missing translation for $tag", File(res, "values-$tag/strings.xml").isFile)
        }
        val translated = res.listFiles { f -> f.name.startsWith("values-") && File(f, "strings.xml").isFile }!!
            .map { it.name.removePrefix("values-") }
            .toSet()
        assertEquals(translated, (UiLanguage.SUPPORTED_TAGS - UiLanguage.DEFAULT_TAG).toSet())
    }
}
