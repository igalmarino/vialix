// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotonLanguageTest {

    @Test
    fun `supported languages map to their two-letter code`() {
        assertEquals("en", photonLanguage("en-US"))
        assertEquals("de", photonLanguage("de-DE"))
        assertEquals("fr", photonLanguage("fr-FR"))
        assertEquals("it", photonLanguage("it-IT"))
    }

    @Test
    fun `unsupported languages are omitted`() {
        assertNull(photonLanguage("pt-BR"))
        assertNull(photonLanguage("es-ES"))
    }

    @Test
    fun `missing tag is omitted`() {
        assertNull(photonLanguage(null))
        assertNull(photonLanguage(""))
    }
}
