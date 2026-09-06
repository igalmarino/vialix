// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.map

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Turns a light MapLibre style into its night version. The ready-made dark styles on the free tile
 * hosts are made for data visualisation (black roads on a near-black ground, dim grey labels, no
 * POIs), so instead the app recolours the light style it already downloads: hue and alpha are kept,
 * lightness and saturation are remapped per [Role], so the map keeps its POIs, shields and road
 * hierarchy and only gets dark.
 *
 * Every `paint` property ending in `-color` is rewritten, including colour literals nested inside
 * expressions (`["interpolate", ...]`, legacy `{"stops": ...}`); strings that are not colours are
 * left alone. Raster layers (the Natural Earth relief at low zoom) get `raster-brightness-max`
 * turned down. Sprite images (POI glyphs, one-way arrows, city dots) are bitmaps and keep their
 * light-theme colours; layers without a `paint` block (the highway shields) are untouched, which is
 * right because their text sits on a light shield image.
 *
 * Pure `org.json` so it is unit-testable; [MapStyleLoader] applies it after [PoiLabelStylePatch]
 * when the dark theme has no dedicated style configured. Tuning happens in [bands] only.
 */
object NightStylePatch {

    /** What a colour is used for; decides which lightness band it lands in. */
    enum class Role {
        /** Background, land use, water, buildings and non-road lines: the dark ground. */
        GROUND,

        /** Road (and runway, rail) fills: clearly lighter than the ground so they read at a glance. */
        ROAD,

        /** Road casings: between ground and road, so the fill stays the lighter of the two as in daylight. */
        CASING,

        /** Label text: light, mirroring how dark it was by day. */
        TEXT,

        /** Label halos: near-black, so they separate text from roads. */
        HALO,
    }

    /** `lightness` maps the daylight lightness (`0..1`) to the night one; saturation is scaled. */
    internal class Band(val lightness: (Double) -> Double, val saturationScale: Double) {
        fun apply(color: CssColor): CssColor = color.copy(
            saturation = (color.saturation * saturationScale).coerceIn(0.0, 1.0),
            lightness = lightness(color.lightness).coerceIn(0.0, 1.0),
        )
    }

    private val bands: Map<Role, Band> =
        mapOf(
            Role.GROUND to Band({ l -> 0.09 + (1 - l) * 0.30 }, saturationScale = 0.4),
            Role.ROAD to Band({ l -> 0.30 + (1 - l) * 0.45 }, saturationScale = 0.7),
            Role.CASING to Band({ l -> 0.18 + (1 - l) * 0.25 }, saturationScale = 0.7),
            Role.TEXT to Band({ l -> (1 - l).coerceIn(0.62, 0.97) }, saturationScale = 0.6),
            Role.HALO to Band({ l -> (1 - l).coerceIn(0.06, 0.20) }, saturationScale = 0.3),
        )

    /** Lines drawn from these source layers are roads (or runways and rails) rather than ground. */
    private val ROAD_SOURCE_LAYERS = setOf("transportation", "aeroway")

    /** Applied to every raster layer; the shaded relief would otherwise glow at low zoom. */
    const val RASTER_BRIGHTNESS_MAX = 0.35

    /** @throws JSONException if [styleJson] is not a JSON object. */
    fun apply(styleJson: String): String {
        val style = JSONObject(styleJson)
        val layers = style.optJSONArray("layers") ?: JSONArray()
        for (i in 0 until layers.length()) {
            val layer = layers.optJSONObject(i) ?: continue
            if (layer.optString("type") == "raster") {
                val paint = layer.optJSONObject("paint") ?: JSONObject().also { layer.put("paint", it) }
                paint.put("raster-brightness-max", RASTER_BRIGHTNESS_MAX)
                continue
            }
            val paint = layer.optJSONObject("paint") ?: continue
            for (key in paint.keys().asSequence().toList()) {
                if (!key.endsWith("-color")) continue
                paint.put(key, recolour(paint.get(key), bands.getValue(roleFor(layer, key))))
            }
        }
        return style.toString()
    }

    internal fun roleFor(layer: JSONObject, property: String): Role = when {
        property == "text-color" || property == "icon-color" -> Role.TEXT

        property == "text-halo-color" || property == "icon-halo-color" -> Role.HALO

        layer.optString("type") == "line" && layer.optString("source-layer") in ROAD_SOURCE_LAYERS ->
            if (layer.optString("id").contains("casing", ignoreCase = true)) Role.CASING else Role.ROAD

        else -> Role.GROUND
    }

    /**
     * Rewrites every colour literal in [value], which may be a string, an expression array or a legacy
     * function object. Always returns a copy: a subtree shared between two properties must not be
     * recoloured twice.
     */
    private fun recolour(value: Any, band: Band): Any = when (value) {
        is String -> CssColor.parse(value)?.let { band.apply(it).format() } ?: value
        is JSONArray -> JSONArray().also { out -> for (i in 0 until value.length()) out.put(recolour(value.get(i), band)) }
        is JSONObject -> JSONObject().also { out -> for (key in value.keys()) out.put(key, recolour(value.get(key), band)) }
        else -> value
    }
}
