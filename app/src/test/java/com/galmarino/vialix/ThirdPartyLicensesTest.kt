// SPDX-FileCopyrightText: 2026 Ignacio Galmarino
// SPDX-License-Identifier: GPL-3.0-or-later

package com.galmarino.vialix

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPartyLicensesTest {

    /** Dependencies that only exist on the unit-test classpath and ship nothing. */
    private val testOnlyGroups = setOf("junit", "org.json")

    @Test
    fun `every entry names a licence and a web page`() {
        for (component in ThirdPartyLicenses.components) {
            assertTrue(component.name, component.license.isNotBlank())
            assertTrue(component.name, component.url.startsWith("https://"))
        }
        assertEquals(ThirdPartyLicenses.components.size, ThirdPartyLicenses.components.map { it.name }.toSet().size)
    }

    /** A dependency added to the version catalog must be credited: the licences screen is the GPL notice for the binary. */
    @Test
    fun `every runtime dependency in the version catalog is credited`() {
        val catalog = listOf(File("../gradle/libs.versions.toml"), File("gradle/libs.versions.toml")).firstOrNull { it.isFile }
        assertTrue("run from the app module or the repository root", catalog != null)
        val groups =
            catalog!!.readLines()
                .mapNotNull { line -> Regex("""group\s*=\s*"([^"]+)"""").find(line)?.groupValues?.get(1) }
                .toSet() - testOnlyGroups
        assertTrue(groups.isNotEmpty())
        val prefixes = ThirdPartyLicenses.components.flatMap { it.groupPrefixes }
        for (group in groups) {
            assertTrue("no licence entry covers $group", prefixes.any { group.startsWith(it) })
        }
    }
}
