package com.galmarino.vialix.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CssColorTest {

    @Test
    fun `hex colours parse in every length and round-trip`() {
        assertEquals("#ffffff", CssColor.parse("#fff")!!.format())
        assertEquals("#ffcc88", CssColor.parse("#fc8")!!.format())
        assertEquals("#e9ac77", CssColor.parse("#e9ac77")!!.format())
        assertEquals("#DEE3CD".lowercase(), CssColor.parse("#DEE3CD")!!.format())
        assertEquals("rgba(255,255,255,0.8)", CssColor.parse("#fffc")!!.format())
        assertEquals("rgba(16,32,48,0.502)", CssColor.parse("#10203080")!!.format())
    }

    @Test
    fun `rgb and rgba parse, spaces included, and keep alpha`() {
        assertEquals("#9ebdff", CssColor.parse("rgb(158,189,255)")!!.format())
        assertEquals("#1b1b1d", CssColor.parse("rgb(27 ,27 ,29)")!!.format())
        assertEquals("#5fd064", CssColor.parse("rgba(95, 208, 100, 1)")!!.format())
        assertEquals("rgba(255,255,255,0.7)", CssColor.parse("rgba(255,255,255,0.7)")!!.format())
        assertEquals("rgba(0,0,0,0.25)", CssColor.parse("rgba(0,0,0,.25)")!!.format())
    }

    @Test
    fun `hsl and hsla parse into their components`() {
        val grey = CssColor.parse("hsl(0,0%,100%)")!!
        assertEquals(0.0, grey.saturation, 1e-9)
        assertEquals(1.0, grey.lightness, 1e-9)
        assertEquals("#ffffff", grey.format())

        val wood = CssColor.parse("hsla(98,61%,72%,0.7)")!!
        assertEquals(98.0, wood.hue, 1e-9)
        assertEquals(0.61, wood.saturation, 1e-9)
        assertEquals(0.72, wood.lightness, 1e-9)
        assertEquals(0.7, wood.alpha, 1e-9)
        assertEquals("rgba(172,227,140,0.7)", wood.format())
    }

    @Test
    fun `rgb to hsl and back is stable`() {
        for (hex in listOf("#f8f4f0", "#a0c8f0", "#666666", "#2e5a80", "#000000", "#fea", "#c16e2f")) {
            val parsed = CssColor.parse(hex)!!
            assertEquals(CssColor.parse(hex)!!.format(), CssColor.parse(parsed.format())!!.format())
        }
        assertEquals("#f8f4f0", CssColor.parse("#f8f4f0")!!.format())
        assertEquals("#2e5a80", CssColor.parse("#2e5a80")!!.format())
    }

    @Test
    fun `hsl lightness of a known colour`() {
        // #666 is 40% grey; #fc8 = rgb(255,204,136) is hsl(34.3,100%,77%).
        assertEquals(0.4, CssColor.parse("#666")!!.lightness, 1e-9)
        val orange = CssColor.parse("#fc8")!!
        assertEquals(34.29, orange.hue, 0.01)
        assertEquals(1.0, orange.saturation, 1e-6)
        assertEquals(0.767, orange.lightness, 0.001)
    }

    @Test
    fun `strings that are not colours are rejected`() {
        assertNull(CssColor.parse("linear"))
        assertNull(CssColor.parse("zoom"))
        assertNull(CssColor.parse("#12345"))
        assertNull(CssColor.parse("#ggg"))
        assertNull(CssColor.parse("rgb(300,0,0)"))
        assertNull(CssColor.parse("rgb(10%,20%,30%)"))
        assertNull(CssColor.parse("white"))
        assertNull(CssColor.parse(""))
        assertNotNull(CssColor.parse(" #fff "))
    }
}
