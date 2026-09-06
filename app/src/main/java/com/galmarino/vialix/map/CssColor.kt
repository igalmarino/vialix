// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.map

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A colour as MapLibre style JSON spells it (`#rgb`, `#rrggbb[aa]`, `rgb[a](...)`, `hsl[a](...)`),
 * kept as HSL so [NightStylePatch] can remap lightness and saturation while preserving hue.
 *
 * Hand-rolled rather than `android.graphics.Color` because `map/` stays free of Android (the
 * platform class is a stub in unit tests and does not parse `hsl()` anyway).
 *
 * @property hue degrees in `[0, 360)`
 * @property saturation `0..1`
 * @property lightness `0..1`
 * @property alpha `0..1`
 */
data class CssColor(val hue: Double, val saturation: Double, val lightness: Double, val alpha: Double = 1.0) {

    /** `#rrggbb` when opaque, otherwise `rgba(r,g,b,a)`; both forms MapLibre accepts. */
    fun format(): String {
        val (r, g, b) = toRgb()
        return if (alpha >= 1.0) {
            String.format(Locale.ROOT, "#%02x%02x%02x", r, g, b)
        } else {
            "rgba($r,$g,$b,${formatAlpha(alpha)})"
        }
    }

    /** Red, green and blue as `0..255` integers. */
    fun toRgb(): Triple<Int, Int, Int> {
        val (r, g, b) =
            if (saturation <= 0.0) {
                Triple(lightness, lightness, lightness)
            } else {
                val q = if (lightness < 0.5) lightness * (1 + saturation) else lightness + saturation - lightness * saturation
                val p = 2 * lightness - q
                val h = hue / 360.0
                Triple(hueToRgb(p, q, h + 1.0 / 3), hueToRgb(p, q, h), hueToRgb(p, q, h - 1.0 / 3))
            }
        return Triple(r.toChannel(), g.toChannel(), b.toChannel())
    }

    companion object {
        private val HEX = Regex("""#([0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})""")
        private val RGB = Regex("""rgba?\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*(?:,\s*(\d*\.?\d+)\s*)?\)""")
        private val HSL = Regex("""hsla?\(\s*(\d*\.?\d+)\s*,\s*(\d*\.?\d+)%\s*,\s*(\d*\.?\d+)%\s*(?:,\s*(\d*\.?\d+)\s*)?\)""")

        /** `null` for anything that is not one of the supported colour syntaxes (`"linear"`, `"zoom"`, ...). */
        fun parse(text: String): CssColor? {
            val value = text.trim()
            HEX.matchEntire(value)?.let { match ->
                var hex = match.groupValues[1]
                if (hex.length <= 4) hex = hex.map { "$it$it" }.joinToString("")
                val alpha = if (hex.length == 8) hex.substring(6, 8).toInt(16) / 255.0 else 1.0
                return fromRgb(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16), alpha)
            }
            RGB.matchEntire(value)?.let { match ->
                val (r, g, b) = match.groupValues.subList(1, 4).map { it.toInt() }
                if (r > 255 || g > 255 || b > 255) return null
                return fromRgb(r, g, b, match.groupValues[4].toAlpha())
            }
            HSL.matchEntire(value)?.let { match ->
                val hue = match.groupValues[1].toDouble().mod(360.0)
                val saturation = (match.groupValues[2].toDouble() / 100).coerceIn(0.0, 1.0)
                val lightness = (match.groupValues[3].toDouble() / 100).coerceIn(0.0, 1.0)
                return CssColor(hue, saturation, lightness, match.groupValues[4].toAlpha())
            }
            return null
        }

        fun fromRgb(red: Int, green: Int, blue: Int, alpha: Double = 1.0): CssColor {
            val r = red / 255.0
            val g = green / 255.0
            val b = blue / 255.0
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val lightness = (max + min) / 2
            if (max == min) return CssColor(0.0, 0.0, lightness, alpha)
            val delta = max - min
            val saturation = if (lightness > 0.5) delta / (2 - max - min) else delta / (max + min)
            val hue =
                when (max) {
                    r -> ((g - b) / delta).mod(6.0)
                    g -> (b - r) / delta + 2
                    else -> (r - g) / delta + 4
                } * 60
            return CssColor(hue, saturation, lightness, alpha)
        }

        private fun String.toAlpha(): Double = if (isEmpty()) 1.0 else toDouble().coerceIn(0.0, 1.0)

        private fun hueToRgb(p: Double, q: Double, hue: Double): Double {
            val t = hue.mod(1.0)
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }

        private fun Double.toChannel(): Int = (this * 255).roundToInt().coerceIn(0, 255)

        private fun formatAlpha(alpha: Double): String =
            BigDecimal(alpha).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
