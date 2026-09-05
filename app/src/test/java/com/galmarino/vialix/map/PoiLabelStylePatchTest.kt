package com.galmarino.vialix.map

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiLabelStylePatchTest {

    private val style =
        """
        {
          "version": 8,
          "sprite": "https://tiles.example.org/sprites/liberty",
          "glyphs": "https://tiles.example.org/fonts/{fontstack}/{range}.pbf",
          "sources": {"openmaptiles": {"type": "vector", "url": "https://tiles.example.org/planet"}},
          "layers": [
            {"id": "water", "type": "fill", "source": "openmaptiles", "source-layer": "water"},
            {"id": "poi_z16", "type": "symbol", "source": "openmaptiles", "source-layer": "poi",
             "layout": {"text-field": "{name}", "text-max-width": 9, "text-letter-spacing": 0.1}},
            {"id": "poi_transit", "type": "symbol", "source": "openmaptiles", "source-layer": "poi"},
            {"id": "place_city", "type": "symbol", "source": "openmaptiles", "source-layer": "place",
             "layout": {"text-max-width": 10}}
          ]
        }
        """

    private fun layer(json: String, id: String): JSONObject {
        val layers = JSONObject(json).getJSONArray("layers")
        return (0 until layers.length()).map { layers.getJSONObject(it) }.first { it.getString("id") == id }
    }

    @Test
    fun `a style without poi layers passes through unchanged`() {
        // Ready-made dark styles such as OpenFreeMap's "dark" label nothing from the poi source layer.
        val dark =
            """
            {"version": 8, "layers": [
              {"id": "water", "type": "fill", "source": "openmaptiles", "source-layer": "water"},
              {"id": "place_city", "type": "symbol", "source": "openmaptiles", "source-layer": "place",
               "layout": {"text-max-width": 10}}
            ]}
            """
        assertEquals(JSONObject(dark).toString(), PoiLabelStylePatch.apply(dark))
    }

    @Test
    fun `poi symbol layers get the wrapping fix`() {
        val patched = PoiLabelStylePatch.apply(style)
        val layout = layer(patched, "poi_z16").getJSONObject("layout")
        assertEquals(PoiLabelStylePatch.TEXT_MAX_WIDTH, layout.getInt("text-max-width"))
        assertEquals(0, layout.getInt("text-letter-spacing"))
        assertEquals("{name}", layout.getString("text-field"))
    }

    @Test
    fun `a poi layer without a layout block gets one`() {
        val layout = layer(PoiLabelStylePatch.apply(style), "poi_transit").getJSONObject("layout")
        assertEquals(PoiLabelStylePatch.TEXT_MAX_WIDTH, layout.getInt("text-max-width"))
    }

    @Test
    fun `other layers are left alone`() {
        val patched = PoiLabelStylePatch.apply(style)
        assertEquals(10, layer(patched, "place_city").getJSONObject("layout").getInt("text-max-width"))
        assertFalse(layer(patched, "water").has("layout"))
    }

    @Test
    fun `poi layers are recognised by source layer or by id`() {
        assertTrue(PoiLabelStylePatch.isPoiLabelLayer(JSONObject("""{"id":"x","type":"symbol","source-layer":"poi"}""")))
        assertTrue(PoiLabelStylePatch.isPoiLabelLayer(JSONObject("""{"id":"POI-labels","type":"symbol"}""")))
        assertFalse(PoiLabelStylePatch.isPoiLabelLayer(JSONObject("""{"id":"poi_bg","type":"circle","source-layer":"poi"}""")))
        assertFalse(PoiLabelStylePatch.isPoiLabelLayer(JSONObject("""{"id":"place","type":"symbol","source-layer":"place"}""")))
    }

    @Test
    fun `styles with absolute urls can be inlined, relative ones cannot`() {
        assertTrue(PoiLabelStylePatch.canBeInlined(style))
        assertFalse(PoiLabelStylePatch.canBeInlined(style.replace("https://tiles.example.org/sprites/liberty", "sprites/liberty")))
        assertFalse(PoiLabelStylePatch.canBeInlined("""{"version":8,"sources":{"a":{"type":"raster","tiles":["/t/{z}/{x}/{y}.png"]}}}"""))
        assertFalse(PoiLabelStylePatch.canBeInlined("not json"))
    }
}
