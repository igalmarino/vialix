package com.galmarino.vialix.navigation

import java.time.LocalDateTime
import kotlin.math.roundToLong

/** The clock time [durationSeconds] from [now]: what the route preview shows as "Arrive 14:32". */
fun arrivalTime(now: LocalDateTime, durationSeconds: Double): LocalDateTime =
    now.plusSeconds(durationSeconds.coerceAtLeast(0.0).roundToLong())
