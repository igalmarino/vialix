package com.galmarino.vialix.navigation

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import uniffi.ferrostar.CourseOverGround
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Speed
import uniffi.ferrostar.UserLocation

class DisplayLocationTest {

    private val now = 1_700_000_000_000L
    private val origin = GeographicCoordinate(lat = 48.8566, lng = 2.3522)

    private fun fix(
        coordinate: GeographicCoordinate = origin,
        speedMps: Double? = 20.0,
        courseDegrees: Int? = 90,
        ageMillis: Long = 500,
    ) = UserLocation(
        coordinates = coordinate,
        horizontalAccuracy = 5.0,
        courseOverGround = courseDegrees?.let { CourseOverGround(it.toUShort(), null) },
        timestamp = Instant.ofEpochMilli(now - ageMillis),
        speed = speedMps?.let { Speed(it, null) },
    )

    private fun shift(from: UserLocation, to: UserLocation) = distanceMeters(from.coordinates, to.coordinates)

    @Test
    fun `moves the fix along its course by fix age plus the animation time`() {
        val fix = fix(speedMps = 20.0, courseDegrees = 90, ageMillis = 500)
        val shown = predictDisplayLocation(fix, previousFix = null, nowEpochMillis = now)
        // (0.5 s + 1 s) * 20 m/s
        assertEquals(30.0, shift(fix, shown), 0.05)
        assertEquals(90.0, bearingDegrees(fix.coordinates, shown.coordinates), 0.01)
        // Everything but the coordinate is passed through.
        assertEquals(fix.copy(coordinates = shown.coordinates), shown)
    }

    @Test
    fun `stationary fix is shown where it is`() {
        val fix = fix(speedMps = 0.3)
        assertSame(fix, predictDisplayLocation(fix, null, now))
    }

    @Test
    fun `no speed and no previous fix means no prediction`() {
        val fix = fix(speedMps = null)
        assertSame(fix, predictDisplayLocation(fix, null, now))
    }

    @Test
    fun `no course and no previous fix means no prediction`() {
        val fix = fix(courseDegrees = null)
        assertSame(fix, predictDisplayLocation(fix, null, now))
    }

    @Test
    fun `speed is derived from the previous fix when the provider omits it`() {
        val previous = fix(speedMps = null, ageMillis = 1500)
        // 15 m east in one second.
        val current = fix(coordinate = origin.moved(15.0, 90.0), speedMps = null, ageMillis = 500)
        val shown = predictDisplayLocation(current, previous, now)
        assertEquals(15.0 * 1.5, shift(current, shown), 0.05)
    }

    @Test
    fun `course is derived from the previous fix when the provider omits it`() {
        val previous = fix(courseDegrees = null, ageMillis = 1500)
        val current = fix(coordinate = origin.moved(15.0, 180.0), courseDegrees = null, ageMillis = 500)
        val shown = predictDisplayLocation(current, previous, now)
        assertEquals(180.0, bearingDegrees(current.coordinates, shown.coordinates), 0.5)
    }

    @Test
    fun `too close previous fix gives no course`() {
        val previous = fix(courseDegrees = null, ageMillis = 1500)
        val current = fix(coordinate = origin.moved(1.0, 180.0), courseDegrees = null, ageMillis = 500)
        assertSame(current, predictDisplayLocation(current, previous, now))
    }

    @Test
    fun `previous fix from long ago is not used`() {
        val previous = fix(speedMps = null, ageMillis = 60_500)
        val current = fix(speedMps = null, ageMillis = 500)
        assertSame(current, predictDisplayLocation(current, previous, now))
    }

    @Test
    fun `fix age is capped`() {
        val fix = fix(speedMps = 20.0, ageMillis = 30_000)
        // (2 s cap + 1 s) * 20 m/s
        assertEquals(60.0, shift(fix, predictDisplayLocation(fix, null, now)), 0.05)
    }

    @Test
    fun `fix from the future counts as fresh`() {
        val fix = fix(speedMps = 20.0, ageMillis = -3_000)
        assertEquals(20.0, shift(fix, predictDisplayLocation(fix, null, now)), 0.05)
    }

    @Test
    fun `shift is capped`() {
        val fix = fix(speedMps = 300.0, ageMillis = 2_000)
        assertEquals(MAX_PREDICTION_METERS, shift(fix, predictDisplayLocation(fix, null, now)), 0.05)
    }

    @Test
    fun `predictor answers the same for the same fix even as time passes`() {
        var clock = now
        val predictor = DisplayLocationPredictor(nowEpochMillis = { clock })
        val fix = fix()
        val first = predictor.predict(fix)
        clock += 700
        assertEquals(first, predictor.predict(fix))
        assertEquals(30.0, shift(fix, first), 0.05)
    }

    @Test
    fun `predictor uses the previous fix for a speedless one`() {
        val predictor = DisplayLocationPredictor(nowEpochMillis = { now })
        predictor.predict(fix(speedMps = null, ageMillis = 1500))
        val current = fix(coordinate = origin.moved(15.0, 90.0), speedMps = null, ageMillis = 500)
        assertEquals(22.5, shift(current, predictor.predict(current)), 0.05)
    }

    @Test
    fun `reset forgets the previous fix`() {
        val predictor = DisplayLocationPredictor(nowEpochMillis = { now })
        predictor.predict(fix(speedMps = null, ageMillis = 1500))
        predictor.reset()
        val current = fix(coordinate = origin.moved(15.0, 90.0), speedMps = null, ageMillis = 500)
        assertSame(current, predictor.predict(current))
    }
}
