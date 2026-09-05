package com.galmarino.vialix.navigation

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivalTimeTest {

    private val now = LocalDateTime.of(2026, 9, 4, 14, 0, 0)

    @Test
    fun `adds the route duration, rounded to the second`() {
        assertEquals(LocalDateTime.of(2026, 9, 4, 14, 32, 30), arrivalTime(now, 1950.4))
    }

    @Test
    fun `crosses midnight`() {
        assertEquals(LocalDateTime.of(2026, 9, 5, 0, 30, 0), arrivalTime(LocalDateTime.of(2026, 9, 4, 23, 45, 0), 2700.0))
    }

    @Test
    fun `a negative duration is treated as zero`() {
        assertEquals(now, arrivalTime(now, -60.0))
    }
}
