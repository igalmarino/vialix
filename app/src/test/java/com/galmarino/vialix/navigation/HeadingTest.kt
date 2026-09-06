package com.galmarino.vialix.navigation

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.CourseOverGround
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Speed
import uniffi.ferrostar.UserLocation

class HeadingTest {

    private fun fix(speedMps: Double?, courseDegrees: Int?) =
        UserLocation(
            coordinates = GeographicCoordinate(lat = 48.8566, lng = 2.3522),
            horizontalAccuracy = 5.0,
            courseOverGround = courseDegrees?.let { CourseOverGround(it.toUShort(), null) },
            timestamp = Instant.ofEpochMilli(1_700_000_000_000L),
            speed = speedMps?.let { Speed(it, null) },
        )

    private fun UserLocation.course(): Int? = courseOverGround?.degrees?.toInt()

    @Test
    fun `normalize wraps into 0 until 360`() {
        assertEquals(330.0, normalizeDegrees(-30.0), 1e-9)
        assertEquals(10.0, normalizeDegrees(370.0), 1e-9)
        assertEquals(0.0, normalizeDegrees(720.0), 1e-9)
    }

    @Test
    fun `screen heading adds the display rotation`() {
        assertEquals(350.0, screenHeadingDegrees(350.0, 0), 1e-9)
        assertEquals(80.0, screenHeadingDegrees(350.0, 90), 1e-9)
        assertEquals(260.0, screenHeadingDegrees(350.0, 270), 1e-9)
    }

    @Test
    fun `no compass heading leaves the fix alone`() {
        val fix = fix(speedMps = 0.0, courseDegrees = null)
        assertSame(fix, fix.withCompassHeading(null))
    }

    @Test
    fun `stationary fix gets the compass heading even with a GPS course`() {
        assertEquals(45, fix(speedMps = 0.2, courseDegrees = 180).withCompassHeading(45).course())
        assertEquals(45, fix(speedMps = 0.2, courseDegrees = null).withCompassHeading(45).course())
    }

    @Test
    fun `moving fix keeps its GPS course`() {
        val fix = fix(speedMps = 12.0, courseDegrees = 180)
        assertSame(fix, fix.withCompassHeading(45))
    }

    @Test
    fun `moving fix without a GPS course gets the compass heading`() {
        assertEquals(45, fix(speedMps = 12.0, courseDegrees = null).withCompassHeading(45).course())
    }

    @Test
    fun `unknown speed trusts a GPS course when there is one`() {
        val withCourse = fix(speedMps = null, courseDegrees = 180)
        assertSame(withCourse, withCourse.withCompassHeading(45))
        assertEquals(45, fix(speedMps = null, courseDegrees = null).withCompassHeading(45).course())
    }

    @Test
    fun `compass heading is normalised and everything else kept`() {
        val fix = fix(speedMps = 0.0, courseDegrees = null)
        val shown = fix.withCompassHeading(360)
        assertEquals(0, shown.course())
        assertNull(shown.courseOverGround?.accuracy)
        assertEquals(fix.copy(courseOverGround = shown.courseOverGround), shown)
    }

    @Test
    fun `smoother starts at the first sample and settles towards later ones`() {
        val smoother = HeadingSmoother(alpha = 0.5)
        assertEquals(90.0, smoother.update(90.0), 1e-9)
        val next = smoother.update(100.0)
        assertTrue("$next", next > 90.0 && next < 100.0)
    }

    @Test
    fun `smoother crosses north the short way`() {
        val smoother = HeadingSmoother(alpha = 0.5)
        smoother.update(350.0)
        val next = smoother.update(10.0)
        // Halfway between 350° and 10° is 0°, not 180°.
        assertTrue("$next", next < 5.0 || next > 355.0)
    }
}
