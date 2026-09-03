/*
 *     Cardinal Maps
 *     Copyright (C) 2026 Cardinal Maps Authors
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

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpeedEstimatorTest {

    private val estimator = SpeedEstimator()

    @Test
    fun `uses provider speed when available`() {
        val previous = location(longitude = 0.0, time = 1_000L)
        val current = location(longitude = 0.0000899, time = 2_000L, speed = 12f)

        val speed = estimator.estimateMetersPerSecond(current, previous)

        assertEquals(12.0, speed!!, 0.0)
    }

    @Test
    fun `falls back to derived speed when provider speed is implausible`() {
        val previous = location(longitude = 0.0, time = 1_000L)
        val current = location(longitude = 0.0000899, time = 2_000L, speed = 200f)

        val speed = estimator.estimateMetersPerSecond(current, previous)

        assertEquals(10.0, speed!!, 0.1)
    }

    @Test
    fun `derives speed from distance and elapsed time`() {
        val previous = location(longitude = 0.0, time = 1_000L)
        val current = location(longitude = 0.0000899, time = 2_000L)

        val speed = estimator.estimateMetersPerSecond(current, previous)

        assertEquals(10.0, speed!!, 0.1)
    }

    @Test
    fun `normalizes very slow movement to zero`() {
        val previous = location(longitude = 0.0, time = 1_000L)
        val current = location(longitude = 0.000001, time = 2_000L)

        val speed = estimator.estimateMetersPerSecond(current, previous)

        assertEquals(0.0, speed!!, 0.0)
    }

    @Test
    fun `ignores derived speed below minimum interval`() {
        val previous = location(longitude = 0.0, time = 1_000L)
        val current = location(longitude = 0.0000899, time = 1_499L)

        val speed = estimator.estimateMetersPerSecond(current, previous)

        assertNull(speed)
    }

    @Test
    fun `ignores derived speed above maximum interval`() {
        val previous = location(longitude = 0.0, time = 1_000L)
        val current = location(longitude = 0.0000899, time = 11_001L)

        val speed = estimator.estimateMetersPerSecond(current, previous)

        assertNull(speed)
    }

    @Test
    fun `ignores implausible derived speed`() {
        val previous = location(longitude = 0.0, time = 1_000L)
        val current = location(longitude = 0.01, time = 2_000L)

        val speed = estimator.estimateMetersPerSecond(current, previous)

        assertNull(speed)
    }

    private fun location(
        longitude: Double,
        time: Long,
        speed: Float? = null
    ): Location {
        return Location("test").apply {
            latitude = 0.0
            this.longitude = longitude
            this.time = time
            speed?.let { this.speed = it }
        }
    }
}
