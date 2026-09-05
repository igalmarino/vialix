package com.galmarino.vialix.location

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import com.galmarino.vialix.navigation.HeadingSmoother
import com.galmarino.vialix.navigation.normalizeDegrees
import com.galmarino.vialix.navigation.screenHeadingDegrees
import kotlin.math.roundToInt
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.sample
import uniffi.ferrostar.UserLocation

/**
 * Where the top of the phone points, from the rotation-vector sensor (gyroscope + magnetometer +
 * accelerometer fused by the platform). Used for the idle puck's heading cone and the heading
 * camera mode while the user stands still and the GPS has no course to offer. The sensor is only
 * registered while a [trueHeadings] flow is collected, so the caller decides when it costs battery.
 */
class CompassHeadingProvider private constructor(
    private val sensorManager: SensorManager,
    private val sensor: Sensor,
    private val displayManager: DisplayManager,
) {
    /**
     * Heading of the top of the screen relative to true north, in whole degrees, corrected for the
     * magnetic declination at the user's position (0 until there is one). Smoothed, and emitted at
     * most every [SAMPLE_MS] and only when it changed, since every value ends up as a new UI state.
     */
    @OptIn(FlowPreview::class)
    fun trueHeadings(locations: Flow<UserLocation?>): Flow<Int> {
        val declination = locations.map { it?.let(::declinationDegrees) ?: 0.0 }.distinctUntilChanged()
        return combine(magneticHeadings(), declination) { magnetic, offset -> normalizeDegrees(magnetic + offset).roundToInt() % 360 }
            .sample(SAMPLE_MS)
            .distinctUntilChanged()
    }

    /** Smoothed magnetic heading of the top of the screen, at the sensor's UI rate. */
    private fun magneticHeadings(): Flow<Double> =
        callbackFlow {
                val rotationVector = FloatArray(4)
                val rotationMatrix = FloatArray(9)
                val orientation = FloatArray(3)
                val smoother = HeadingSmoother()
                val listener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
                            // Some devices report a fifth (accuracy) value; the matrix wants four.
                            System.arraycopy(event.values, 0, rotationVector, 0, minOf(4, event.values.size))
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)
                            SensorManager.getOrientation(rotationMatrix, orientation)
                            val azimuth = Math.toDegrees(orientation[0].toDouble())
                            trySend(smoother.update(screenHeadingDegrees(azimuth, displayRotationDegrees())))
                        }

                        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
                    }
                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                awaitClose { sensorManager.unregisterListener(listener) }
            }
            .conflate()

    private fun displayRotationDegrees(): Int =
        when (displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }

    companion object {
        /** Ten updates a second is plenty for a cone and a rotating map, and cheap on recomposition. */
        const val SAMPLE_MS = 100L

        /** `null` when the device has no rotation-vector sensor; the puck then only shows the GPS course. */
        fun create(context: Context): CompassHeadingProvider? {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: return null
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            return CompassHeadingProvider(sensorManager, sensor, displayManager)
        }
    }
}

/** Difference between magnetic and true north at [location], degrees east positive. */
private fun declinationDegrees(location: UserLocation): Double =
    GeomagneticField(
            location.coordinates.lat.toFloat(),
            location.coordinates.lng.toFloat(),
            0f,
            System.currentTimeMillis(),
        )
        .declination
        .toDouble()
