/*
 *     Cardinal Maps
 *     Copyright (C) 2025 Cardinal Maps Authors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package earth.maps.cardinal.data

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Repository for handling device orientation using sensor data.
 * Uses ROTATION_VECTOR sensor which provides fused sensor data for better accuracy,
 * especially on devices with noisy magnetometers.
 */
@Singleton
class OrientationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private companion object {
        private const val TAG = "OrientationRepository"

        // Sensor update rate - SENSOR_DELAY_UI is ~60ms, good balance between accuracy and battery
        private const val SENSOR_DELAY = SensorManager.SENSOR_DELAY_UI

        // Low-pass filter alpha value (0.0-1.0)
        // Lower values = more smoothing but more lag
        // Higher values = less smoothing but more responsive
        private const val SMOOTHING_FACTOR = 0.15f

        // Threshold in degrees to ignore small changes (reduces jitter)
        private const val CHANGE_THRESHOLD_DEGREES = 2.0f
    }

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // Current azimuth in degrees (0-360, where 0/360 is North, 90 is East, etc.)
    private val _azimuth = MutableStateFlow(0f)
    val azimuth: StateFlow<Float> = _azimuth.asStateFlow()

    // Indicates if the sensor is currently active
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    // Smoothed azimuth value for filtering out noise
    private var smoothedAzimuth: Float = 0f

    // Rotation matrix and orientation arrays for sensor calculations
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var declination = 0f

    private val sensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let { handleSensorEvent(it) }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Log accuracy changes for debugging
            when (accuracy) {
                SensorManager.SENSOR_STATUS_UNRELIABLE ->
                    Log.d(TAG, "Sensor accuracy: UNRELIABLE")

                SensorManager.SENSOR_STATUS_ACCURACY_LOW ->
                    Log.d(TAG, "Sensor accuracy: LOW")

                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM ->
                    Log.d(TAG, "Sensor accuracy: MEDIUM")

                SensorManager.SENSOR_STATUS_ACCURACY_HIGH ->
                    Log.d(TAG, "Sensor accuracy: HIGH")
            }
        }
    }

    /**
     * Starts listening to orientation sensor updates.
     */
    fun start() {
        if (_isActive.value) {
            Log.d(TAG, "Orientation sensor already active")
            return
        }

        if (!areSensorsAvailable()) {
            Log.w(TAG, "ROTATION_VECTOR sensor not available on this device")
            return
        }

        val registered = sensorManager.registerListener(
            sensorEventListener,
            rotationVectorSensor,
            SENSOR_DELAY
        )

        if (registered) {
            _isActive.value = true
            Log.d(TAG, "Orientation sensor started")
        } else {
            Log.e(TAG, "Failed to register orientation sensor listener")
        }
    }

    /**
     * Stops listening to orientation sensor updates.
     */
    fun stop() {
        if (!_isActive.value) {
            return
        }

        sensorManager.unregisterListener(sensorEventListener)
        _isActive.value = false
        Log.d(TAG, "Orientation sensor stopped")
    }

    fun setDeclination(location: Location) {
        declination = GeomagneticField(
            location.latitude.toFloat(),
            location.longitude.toFloat(),
            0.0f,
            System.currentTimeMillis()
        ).declination
    }

    /**
     * Resets the orientation filter.
     * Useful after device calibration or when heading seems incorrect.
     */
    fun reset() {
        smoothedAzimuth = _azimuth.value
        Log.d(TAG, "Orientation filter reset to current azimuth: $smoothedAzimuth")
    }

    /**
     * Checks if orientation sensors are available on this device.
     */
    fun areSensorsAvailable(): Boolean {
        return rotationVectorSensor != null
    }

    /**
     * Handles sensor events and updates the azimuth value.
     */
    private fun handleSensorEvent(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) {
            return
        }

        // Convert rotation vector to rotation matrix
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Get orientation angles from rotation matrix
        // orientationAngles[0] = azimuth (rotation around Z axis)
        // orientationAngles[1] = pitch (rotation around X axis)
        // orientationAngles[2] = roll (rotation around Y axis)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // Convert azimuth from radians to degrees
        // Result is in range [-180, 180], convert to [0, 360]
        var azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
        if (azimuthDegrees < 0) {
            azimuthDegrees += 360f
        }

        // Apply declination correction to convert from magnetic north to true north
        // Formula: True Heading = Magnetic Heading + Declination
        azimuthDegrees += declination
        
        // Normalize to [0, 360] after adding declination
        if (azimuthDegrees < 0f) {
            azimuthDegrees += 360f
        } else if (azimuthDegrees >= 360f) {
            azimuthDegrees -= 360f
        }

        // Apply low-pass filter for smoothing
        smoothedAzimuth = applyLowPassFilter(azimuthDegrees, smoothedAzimuth)

        // Only update if change is significant (reduces jitter)
        if (shouldUpdateAzimuth(smoothedAzimuth, _azimuth.value)) {
            _azimuth.value = smoothedAzimuth
        }
    }

    /**
     * Applies a low-pass filter to smooth out sensor noise.
     *
     * @param newValue The new sensor reading
     * @param oldValue The previous filtered value
     * @return The filtered value
     */
    private fun applyLowPassFilter(newValue: Float, oldValue: Float): Float {
        // Handle wrap-around at 0/360 degrees
        var delta = newValue - oldValue

        // If the difference is greater than 180 degrees, we've wrapped around
        if (delta > 180f) {
            delta -= 360f
        } else if (delta < -180f) {
            delta += 360f
        }

        // Apply the filter
        var result = oldValue + (delta * SMOOTHING_FACTOR)

        // Normalize to [0, 360]
        if (result < 0f) {
            result += 360f
        } else if (result >= 360f) {
            result -= 360f
        }

        return result
    }

    /**
     * Determines if the azimuth should be updated based on the change threshold.
     *
     * @param newValue The new azimuth value
     * @param currentValue The current azimuth value
     * @return true if the change exceeds the threshold
     */
    private fun shouldUpdateAzimuth(newValue: Float, currentValue: Float): Boolean {
        val delta = abs(newValue - currentValue)

        // Handle wrap-around case
        val normalizedDelta = if (delta > 180f) {
            360f - delta
        } else {
            delta
        }

        return normalizedDelta >= CHANGE_THRESHOLD_DEGREES
    }
}
