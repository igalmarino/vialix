// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.map

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The patches against a snapshot of the style the app actually ships with, OpenFreeMap "liberty"
 * (`src/test/resources/openfreemap-liberty.json`, fetched 2026-09; OpenMapTiles / OpenFreeMap,
 * BSD-3-Clause). The unit tests on synthetic styles pin the rules; this one catches an upstream
 * change of shape that would leave the dark map half light without any test noticing.
 */
class LibertyStyleTest {

    private val liberty = checkNotNull(javaClass.getResource("/openfreemap-liberty.json")) { "fixture missing" }.readText()

    @Test
    fun `the shipped style can be inlined and patched`() {
        assertTrue(PoiLabelStylePatch.canBeInlined(liberty))
        val patched = JSONObject(NightStylePatch.apply(PoiLabelStylePatch.apply(liberty)))
        val original = JSONObject(liberty)
        assertEquals(original.getJSONArray("layers").length(), patched.getJSONArray("layers").length())
        assertEquals(original.getString("sprite"), patched.getString("sprite"))
        assertEquals(original.getString("glyphs"), patched.getString("glyphs"))
        assertEquals(original.getJSONObject("sources").toString(), patched.getJSONObject("sources").toString())
    }

    @Test
    fun `the night version has a dark ground and light text everywhere`() {
        val layers = JSONObject(NightStylePatch.apply(liberty)).getJSONArray("layers")
        var groundColours = 0
        var textColours = 0
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            val paint = layer.optJSONObject("paint") ?: continue
            for (key in paint.keys()) {
                if (!key.endsWith("-color")) continue
                val role = NightStylePatch.roleFor(layer, key)
                for (colour in colourLiterals(paint.get(key))) {
                    val lightness = checkNotNull(CssColor.parse(colour)) { "$colour in ${layer.getString("id")}" }.lightness
                    when (role) {
                        NightStylePatch.Role.GROUND -> {
                            groundColours++
                            assertTrue("${layer.getString("id")} $key $colour", lightness <= 0.40)
                        }

                        NightStylePatch.Role.TEXT -> {
                            textColours++
                            assertTrue("${layer.getString("id")} $key $colour", lightness >= 0.60)
                        }

                        else -> Unit
                    }
                }
            }
        }
        // The style really exercises both bands.
        assertTrue(groundColours > 20)
        assertTrue(textColours > 10)
    }

    @Test
    fun `POI labels get the wrapping fix`() {
        val layers = JSONObject(PoiLabelStylePatch.apply(liberty)).getJSONArray("layers")
        var poiLayers = 0
        for (i in 0 until layers.length()) {
            val layer = layers.getJSONObject(i)
            if (layer.optString("type") != "symbol" || layer.optString("source-layer") != "poi") continue
            poiLayers++
            assertEquals(layer.getString("id"), 8, layer.getJSONObject("layout").getInt("text-max-width"))
        }
        assertTrue(poiLayers > 0)
    }

    /** Every string that parses as a colour anywhere inside a paint value (literal, expression or legacy stops). */
    private fun colourLiterals(value: Any): List<String> = when (value) {
        is String -> if (CssColor.parse(value) != null) listOf(value) else emptyList()
        is JSONArray -> (0 until value.length()).flatMap { colourLiterals(value.get(it)) }
        is JSONObject -> value.keys().asSequence().flatMap { colourLiterals(value.get(it)).asSequence() }.toList()
        else -> emptyList()
    }
}
