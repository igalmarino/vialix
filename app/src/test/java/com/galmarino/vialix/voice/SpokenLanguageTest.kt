package com.galmarino.vialix.voice

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenLanguageTest {

    @Test
    fun `a supported locale is used as is`() {
        assertEquals("de-DE", SpokenLanguage.valhallaTag(Locale.forLanguageTag("de-DE")))
        assertEquals("en-GB", SpokenLanguage.valhallaTag(Locale.forLanguageTag("en-GB")))
    }

    @Test
    fun `an unsupported region falls back to the same language`() {
        assertEquals("es-ES", SpokenLanguage.valhallaTag(Locale.forLanguageTag("es-AR")))
        assertEquals("en-US", SpokenLanguage.valhallaTag(Locale.forLanguageTag("en-AU")))
        assertEquals("pt-PT", SpokenLanguage.valhallaTag(Locale.forLanguageTag("pt-AO")))
    }

    @Test
    fun `a language with no region still resolves`() {
        assertEquals("fr-FR", SpokenLanguage.valhallaTag(Locale.forLanguageTag("fr")))
    }

    @Test
    fun `an unsupported language falls back to English`() {
        assertEquals(SpokenLanguage.DEFAULT_TAG, SpokenLanguage.valhallaTag(Locale.forLanguageTag("is-IS")))
        assertEquals(SpokenLanguage.DEFAULT_TAG, SpokenLanguage.valhallaTag(Locale.ROOT))
    }

    @Test
    fun `every supported tag resolves to itself`() {
        SpokenLanguage.SUPPORTED_TAGS.forEach { tag ->
            assertEquals(tag, SpokenLanguage.valhallaTag(Locale.forLanguageTag(tag)))
        }
    }

    @Test
    fun `the default tag is supported`() {
        assertTrue(SpokenLanguage.DEFAULT_TAG in SpokenLanguage.SUPPORTED_TAGS)
    }
}
