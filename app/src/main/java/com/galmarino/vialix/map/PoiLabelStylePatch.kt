package com.galmarino.vialix.map

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Fixes POI label wrapping in a MapLibre style: public OpenStreetMap styles let long names such as
 * "Muebles Rústicos de Colombia" break mid-word. Every POI symbol layer gets
 * `text-max-width` = [TEXT_MAX_WIDTH] ems and `text-letter-spacing` = 0.
 *
 * Pure `org.json` so it is unit-testable; [MapStyleLoader] applies it to the downloaded style.
 */
object PoiLabelStylePatch {

    const val TEXT_MAX_WIDTH = 8

    /** @throws JSONException if [styleJson] is not a JSON object. */
    fun apply(styleJson: String): String {
        val style = JSONObject(styleJson)
        val layers = style.optJSONArray("layers") ?: JSONArray()
        for (i in 0 until layers.length()) {
            val layer = layers.optJSONObject(i) ?: continue
            if (!isPoiLabelLayer(layer)) continue
            val layout = layer.optJSONObject("layout") ?: JSONObject().also { layer.put("layout", it) }
            layout.put("text-max-width", TEXT_MAX_WIDTH)
            layout.put("text-letter-spacing", 0)
        }
        return style.toString()
    }

    /** Symbol layers drawn from the `poi` source layer, or whose id says so. */
    internal fun isPoiLabelLayer(layer: JSONObject): Boolean =
        layer.optString("type") == "symbol" &&
            (layer.optString("source-layer") == "poi" || layer.optString("id").contains("poi", ignoreCase = true))

    /**
     * A style handed to MapLibre as inline JSON has no base URL, so relative sprite, glyph or
     * tile URLs would break. Only styles that reference everything absolutely can be inlined.
     */
    fun canBeInlined(styleJson: String): Boolean =
        try {
            val style = JSONObject(styleJson)
            val urls = buildList {
                if (style.has("sprite")) add(style.get("sprite"))
                if (style.has("glyphs")) add(style.get("glyphs"))
                style.optJSONObject("sources")?.let { sources ->
                    sources.keys().forEach { name ->
                        val source = sources.optJSONObject(name) ?: return@forEach
                        if (source.has("url")) add(source.get("url"))
                        source.optJSONArray("tiles")?.let { tiles -> for (i in 0 until tiles.length()) add(tiles.get(i)) }
                    }
                }
            }
            urls.all { it is String && it.isAbsoluteUrl() }
        } catch (e: JSONException) {
            false
        }

    private fun String.isAbsoluteUrl(): Boolean =
        startsWith("http://") || startsWith("https://") || startsWith("asset://") || startsWith("mapbox://")
}
