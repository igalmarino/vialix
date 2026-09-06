// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.galmarino.vialix.ui.theme.LocationBlue
import com.galmarino.vialix.ui.theme.LocationBlueDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two palettes drawn over and into the map, one per basemap. */
class MapPaintTest {

    @Test
    fun `light paint is the original palette`() {
        val paint = mapPaint(dark = false)
        assertEquals(LocationBlue.toArgb(), paint.location.toArgb())
        assertEquals(Color.White.toArgb(), paint.locationRing.toArgb())
        assertEquals(0xFFD32F2F.toInt(), paint.pin.toArgb())
        assertEquals(0xFFB4BCC6.toInt(), paint.alternativeRoute.toArgb())
        assertEquals(0xFF5B6B7C.toInt(), paint.selectedRoute.toArgb())
        assertEquals(0xFF00796B.toInt(), paint.favorite.toArgb())
        assertEquals(Color.White.toArgb(), paint.pinRing.toArgb())
    }

    @Test
    fun `dark paint lifts what must read on a near-black map and drops what must recede`() {
        val light = mapPaint(dark = false)
        val dark = mapPaint(dark = true)
        assertEquals(LocationBlueDark.toArgb(), dark.location.toArgb())
        assertTrue(dark.location.luminance() > light.location.luminance())
        assertTrue(dark.pin.luminance() > light.pin.luminance())
        assertTrue(dark.favorite.luminance() > light.favorite.luminance())
        assertTrue(dark.selectedRoute.luminance() > light.selectedRoute.luminance())
        assertTrue(dark.alternativeRoute.luminance() < light.alternativeRoute.luminance())
        // The selected route must stand out from the alternatives in both.
        assertTrue(dark.selectedRoute.luminance() > dark.alternativeRoute.luminance())
        assertTrue(light.selectedRoute.luminance() < light.alternativeRoute.luminance())
        // The Home/Work marker must stay a third colour, distinct from the puck and the pin.
        for (paint in listOf(light, dark)) {
            assertTrue(paint.favorite.toArgb() != paint.location.toArgb())
            assertTrue(paint.favorite.toArgb() != paint.pin.toArgb())
        }
    }

    @Test
    fun `light chrome is white with dark content`() {
        val chrome = mapChromeColors(dark = false)
        assertEquals(Color.White.toArgb(), chrome.surface.toArgb())
        assertTrue(chrome.content.luminance() < chrome.surface.luminance())
        assertEquals(LocationBlue.toArgb(), chrome.accent.toArgb())
    }

    @Test
    fun `dark chrome is dark with light content and a light accent`() {
        val chrome = mapChromeColors(dark = true)
        assertTrue(chrome.surface.luminance() < 0.15f)
        assertTrue(chrome.content.luminance() > chrome.surface.luminance())
        assertTrue(chrome.hint.luminance() > chrome.surface.luminance())
        assertTrue(chrome.accent.luminance() > chrome.surface.luminance())
        assertEquals(LocationBlueDark.toArgb(), chrome.accent.toArgb())
    }
}
