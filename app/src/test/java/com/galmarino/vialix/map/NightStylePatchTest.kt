package com.galmarino.vialix.map

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightStylePatchTest {

    /** A slice of OpenFreeMap "liberty" with one layer per colour role. */
    private val style =
        """
        {
          "version": 8,
          "sprite": "https://tiles.example.org/sprites/ofm",
          "glyphs": "https://tiles.example.org/fonts/{fontstack}/{range}.pbf",
          "sources": {
            "openmaptiles": {"type": "vector", "url": "https://tiles.example.org/planet"},
            "ne2_shaded": {"type": "raster", "tiles": ["https://tiles.example.org/ne/{z}/{x}/{y}.png"]}
          },
          "layers": [
            {"id": "background", "type": "background", "paint": {"background-color": "#f8f4f0"}},
            {"id": "natural_earth", "type": "raster", "source": "ne2_shaded",
             "paint": {"raster-opacity": ["interpolate", ["exponential", 1.5], ["zoom"], 0, 0.6, 6, 0.1]}},
            {"id": "water", "type": "fill", "source": "openmaptiles", "source-layer": "water",
             "paint": {"fill-color": "rgb(158,189,255)"}},
            {"id": "landuse_residential", "type": "fill", "source": "openmaptiles", "source-layer": "landuse",
             "paint": {"fill-color": ["interpolate", ["linear"], ["zoom"], 9, "hsla(0,3%,85%,0.84)", 12, "hsla(35,57%,88%,0.49)"]}},
            {"id": "building", "type": "fill", "source": "openmaptiles", "source-layer": "building",
             "paint": {"fill-color": "hsl(35,8%,85%)", "fill-outline-color": {"stops": [[13, "hsla(35,6%,79%,0.32)"], [14, "hsl(35,6%,79%)"]]}}},
            {"id": "waterway_river", "type": "line", "source": "openmaptiles", "source-layer": "waterway",
             "paint": {"line-color": "#a0c8f0", "line-width": 2}},
            {"id": "road_minor_casing", "type": "line", "source": "openmaptiles", "source-layer": "transportation",
             "paint": {"line-color": "#cfcdca", "line-opacity": ["interpolate", ["linear"], ["zoom"], 12, 0, 12.5, 1]}},
            {"id": "road_minor", "type": "line", "source": "openmaptiles", "source-layer": "transportation",
             "paint": {"line-color": "#fff"}},
            {"id": "road_motorway", "type": "line", "source": "openmaptiles", "source-layer": "transportation",
             "paint": {"line-color": ["interpolate", ["linear"], ["zoom"], 5, "hsl(26,87%,62%)", 6, "#fc8"]}},
            {"id": "aeroway_runway", "type": "line", "source": "openmaptiles", "source-layer": "aeroway",
             "paint": {"line-color": "#f0ede9"}},
            {"id": "road_one_way_arrow", "type": "symbol", "source": "openmaptiles", "source-layer": "transportation",
             "layout": {"icon-image": "arrow", "symbol-placement": "line"}},
            {"id": "highway-shield-non-us", "type": "symbol", "source": "openmaptiles", "source-layer": "transportation_name",
             "layout": {"icon-image": ["concat", "road_", ["get", "ref_length"]], "text-field": "{ref}"}},
            {"id": "poi_r20", "type": "symbol", "source": "openmaptiles", "source-layer": "poi",
             "layout": {"text-field": "{name}", "text-max-width": 8},
             "paint": {"text-color": "#666", "text-halo-blur": 0.5, "text-halo-color": "#ffffff", "text-halo-width": 1}},
            {"id": "label_city", "type": "symbol", "source": "openmaptiles", "source-layer": "place",
             "paint": {"text-color": "#000", "text-halo-color": "rgba(255,255,255,0.7)", "text-halo-width": 1}}
          ]
        }
        """

    private val patched: JSONObject by lazy { JSONObject(NightStylePatch.apply(style)) }

    private fun layer(id: String, json: JSONObject = patched): JSONObject {
        val layers = json.getJSONArray("layers")
        return (0 until layers.length()).map { layers.getJSONObject(it) }.first { it.getString("id") == id }
    }

    private fun paint(id: String): JSONObject = layer(id).getJSONObject("paint")

    private fun lightness(colour: String): Double = CssColor.parse(colour)!!.lightness

    private fun lightness(id: String, property: String): Double = lightness(paint(id).getString(property))

    @Test
    fun `the ground ends dark and keeps its hue`() {
        val ground = CssColor.parse(paint("background").getString("background-color"))!!
        assertTrue("ground lightness ${ground.lightness}", ground.lightness < 0.15)
        assertEquals(30.0, ground.hue, 1.0)
        assertTrue(lightness("water", "fill-color") < 0.2)
        assertTrue(lightness("building", "fill-color") < 0.2)
        assertTrue(lightness("waterway_river", "line-color") < 0.2)
        assertEquals(221.0, CssColor.parse(paint("water").getString("fill-color"))!!.hue, 1.0)
    }

    @Test
    fun `roads end lighter than the ground and than their casing`() {
        val ground = lightness("background", "background-color")
        val minor = lightness("road_minor", "line-color")
        val casing = lightness("road_minor_casing", "line-color")
        val runway = lightness("aeroway_runway", "line-color")
        assertTrue("road $minor vs ground $ground", minor > ground + 0.15)
        assertTrue("casing $casing between ground $ground and road $minor", casing > ground && casing < minor)
        assertTrue(runway > ground + 0.15)
    }

    @Test
    fun `labels end light with near-black halos and their alpha kept`() {
        assertTrue(lightness("label_city", "text-color") >= 0.95)
        assertTrue(lightness("poi_r20", "text-color") in 0.55..0.7)
        assertTrue(lightness("poi_r20", "text-halo-color") <= 0.1)
        val cityHalo = CssColor.parse(paint("label_city").getString("text-halo-color"))!!
        assertTrue(cityHalo.lightness <= 0.2)
        assertEquals(0.7, cityHalo.alpha, 1e-9)
        // Nothing but colours changes.
        assertEquals(1, paint("label_city").getInt("text-halo-width"))
        assertEquals(0.5, paint("poi_r20").getDouble("text-halo-blur"), 0.0)
        assertEquals(8, layer("poi_r20").getJSONObject("layout").getInt("text-max-width"))
    }

    @Test
    fun `colours inside expressions and legacy functions are rewritten, the rest survives`() {
        val motorway = paint("road_motorway").getJSONArray("line-color")
        assertEquals("interpolate", motorway.getString(0))
        assertEquals("linear", motorway.getJSONArray(1).getString(0))
        assertEquals("zoom", motorway.getJSONArray(2).getString(0))
        assertEquals(5, motorway.getInt(3))
        assertTrue(lightness(motorway.getString(4)) in 0.3..0.6)
        assertTrue(lightness(motorway.getString(6)) in 0.3..0.6)
        assertEquals(26.0, CssColor.parse(motorway.getString(4))!!.hue, 1.0)

        val residential = paint("landuse_residential").getJSONArray("fill-color")
        val stop = CssColor.parse(residential.getString(4))!!
        assertTrue(stop.lightness < 0.2)
        assertEquals(0.84, stop.alpha, 1e-9)

        val outline = paint("building").getJSONObject("fill-outline-color").getJSONArray("stops")
        assertEquals(13, outline.getJSONArray(0).getInt(0))
        assertTrue(lightness(outline.getJSONArray(0).getString(1)) < 0.2)
        assertEquals(0.32, CssColor.parse(outline.getJSONArray(0).getString(1))!!.alpha, 1e-9)
    }

    @Test
    fun `non-colour paint properties and layers without paint are untouched`() {
        assertEquals(2, paint("waterway_river").getInt("line-width"))
        assertEquals(
            JSONArray("""["interpolate", ["linear"], ["zoom"], 12, 0, 12.5, 1]""").toString(),
            paint("road_minor_casing").getJSONArray("line-opacity").toString(),
        )
        val original = JSONObject(style)
        assertEquals(layer("highway-shield-non-us", original).toString(), layer("highway-shield-non-us").toString())
        assertEquals(layer("road_one_way_arrow", original).toString(), layer("road_one_way_arrow").toString())
        assertEquals(original.getString("sprite"), patched.getString("sprite"))
        assertEquals(original.getJSONObject("sources").toString(), patched.getJSONObject("sources").toString())
    }

    @Test
    fun `raster layers are dimmed`() {
        val raster = paint("natural_earth")
        assertEquals(NightStylePatch.RASTER_BRIGHTNESS_MAX, raster.getDouble("raster-brightness-max"), 0.0)
        assertEquals("interpolate", raster.getJSONArray("raster-opacity").getString(0))
        val bare = NightStylePatch.apply("""{"version":8,"layers":[{"id":"r","type":"raster","source":"s"}]}""")
        assertEquals(
            NightStylePatch.RASTER_BRIGHTNESS_MAX,
            JSONObject(bare).getJSONArray("layers").getJSONObject(0).getJSONObject("paint").getDouble("raster-brightness-max"),
            0.0,
        )
    }

    @Test
    fun `the patched style can still be inlined`() {
        assertTrue(PoiLabelStylePatch.canBeInlined(NightStylePatch.apply(style)))
    }

    @Test
    fun `roles follow the property, then the layer`() {
        fun layer(json: String) = JSONObject(json)
        val road = layer("""{"id":"road_minor","type":"line","source-layer":"transportation"}""")
        val casing = layer("""{"id":"bridge_street_casing","type":"line","source-layer":"transportation"}""")
        val runway = layer("""{"id":"aeroway_runway","type":"line","source-layer":"aeroway"}""")
        val river = layer("""{"id":"waterway_river","type":"line","source-layer":"waterway"}""")
        val fill = layer("""{"id":"water","type":"fill","source-layer":"water"}""")
        val label = layer("""{"id":"label_city","type":"symbol","source-layer":"place"}""")

        assertEquals(NightStylePatch.Role.ROAD, NightStylePatch.roleFor(road, "line-color"))
        assertEquals(NightStylePatch.Role.CASING, NightStylePatch.roleFor(casing, "line-color"))
        assertEquals(NightStylePatch.Role.ROAD, NightStylePatch.roleFor(runway, "line-color"))
        assertEquals(NightStylePatch.Role.GROUND, NightStylePatch.roleFor(river, "line-color"))
        assertEquals(NightStylePatch.Role.GROUND, NightStylePatch.roleFor(fill, "fill-color"))
        assertEquals(NightStylePatch.Role.GROUND, NightStylePatch.roleFor(fill, "fill-outline-color"))
        assertEquals(NightStylePatch.Role.TEXT, NightStylePatch.roleFor(label, "text-color"))
        assertEquals(NightStylePatch.Role.TEXT, NightStylePatch.roleFor(label, "icon-color"))
        assertEquals(NightStylePatch.Role.HALO, NightStylePatch.roleFor(label, "text-halo-color"))
        assertEquals(NightStylePatch.Role.HALO, NightStylePatch.roleFor(road, "icon-halo-color"))
        assertFalse(NightStylePatch.roleFor(road, "line-color") == NightStylePatch.roleFor(casing, "line-color"))
    }
}
