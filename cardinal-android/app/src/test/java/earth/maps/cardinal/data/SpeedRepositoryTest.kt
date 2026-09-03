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
class SpeedRepositoryTest {

    private val repository = SpeedRepository(SpeedEstimator())

    @Test
    fun `publishLocation emits estimated speed with location timestamp`() {
        repository.publishLocation(location(time = 1_234L, speed = 8f))

        assertEquals(
            UserSpeed(metersPerSecond = 8.0, timestampMillis = 1_234L),
            repository.speedFlow.value
        )
    }

    @Test
    fun `publishLocation emits null when speed cannot be estimated`() {
        repository.publishLocation(location(time = 1_234L))

        assertNull(repository.speedFlow.value)
    }

    @Test
    fun `reset clears current and previous speed state`() {
        repository.publishLocation(location(longitude = 0.0, time = 1_000L))
        repository.publishLocation(location(longitude = 0.0000899, time = 2_000L))

        repository.reset()
        repository.publishLocation(location(longitude = 0.0001798, time = 3_000L))

        assertNull(repository.speedFlow.value)
    }

    private fun location(
        longitude: Double = 0.0,
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
