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
package earth.maps.cardinal.data.navigation

import earth.maps.cardinal.data.LatLng
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CustomDeviationDetectorTest {

    private val distanceCalculator = mockk<PolylineDistanceCalculator>(relaxed = true)
    private var nowMillis = 0L

    private lateinit var detector: CustomDeviationDetector

    @Before
    fun setup() {
        nowMillis = 0L
        detector = CustomDeviationDetector(distanceCalculator) { nowMillis }
    }

    private fun latLng(lat: Double, lon: Double) = LatLng(lat, lon)

    private val route = listOf(
        latLng(0.0, 0.0),
        latLng(0.0, 1.0)
    )

    @Test
    fun `should return false when route has less than 2 points`() {
        val result = detector.isOffRoute(
            location = latLng(0.0, 0.0),
            route = listOf(latLng(0.0, 0.0))
        )

        assertFalse(result)
        verify(exactly = 0) { distanceCalculator.distanceFromPolyline(any(), any()) }
    }

    @Test
    fun `should return false when distance is within threshold`() {
        every {
            distanceCalculator.distanceFromPolyline(any(), any())
        } returns 10.0 // below 15

        val result = detector.isOffRoute(latLng(0.0, 0.5), route)

        assertFalse(result)
    }

    @Test
    fun `should return true when distance exceeds threshold for debounce period`() {
        every {
            distanceCalculator.distanceFromPolyline(any(), any())
        } returns 20.0

        val first = detector.isOffRoute(latLng(1.0, 1.0), route)
        nowMillis = 1500L
        val second = detector.isOffRoute(latLng(1.0, 1.0), route)

        assertFalse(first)
        assertTrue(second)
    }

    @Test
    fun `should wait for debounce period before confirming off route`() {
        every {
            distanceCalculator.distanceFromPolyline(any(), any())
        } returns 20.0

        val location = latLng(1.0, 1.0)

        val first = detector.isOffRoute(location, route)
        nowMillis = 1000L
        val second = detector.isOffRoute(location, route)

        assertFalse(first)
        assertFalse(second)
    }

    @Test
    fun `should keep returning true while confirmed off route`() {
        every {
            distanceCalculator.distanceFromPolyline(any(), any())
        } returns 20.0

        val location = latLng(1.0, 1.0)

        val first = detector.isOffRoute(location, route)
        nowMillis = 1500L
        val second = detector.isOffRoute(location, route)
        nowMillis = 1600L
        val third = detector.isOffRoute(location, route)

        assertFalse(first)
        assertTrue(second)
        assertTrue(third)
    }

    @Test
    fun `should return to route only within return threshold`() {
        every {
            distanceCalculator.distanceFromPolyline(any(), any())
        } returnsMany listOf(20.0, 20.0, 10.0, 8.0)

        val location = latLng(1.0, 1.0)

        val first = detector.isOffRoute(location, route)
        nowMillis = 1500L
        val second = detector.isOffRoute(location, route)
        val third = detector.isOffRoute(location, route)
        val fourth = detector.isOffRoute(location, route)

        assertFalse(first)
        assertTrue(second)
        assertTrue(third)
        assertFalse(fourth)
    }

    @Test
    fun `should reset cache when route changes`() {
        every {
            distanceCalculator.distanceFromPolyline(any(), any())
        } returns 10.0

        val route1 = listOf(latLng(0.0, 0.0), latLng(0.0, 1.0))
        val route2 = listOf(latLng(1.0, 1.0), latLng(2.0, 2.0))

        detector.isOffRoute(latLng(0.0, 0.5), route1)
        detector.isOffRoute(latLng(0.0, 0.5), route2)

        verify(atLeast = 1) {
            distanceCalculator.resetCache()
        }
    }

    @Test
    fun `should not reset cache for same route`() {
        every {
            distanceCalculator.distanceFromPolyline(any(), any())
        } returns 10.0

        detector.isOffRoute(latLng(0.0, 0.5), route)
        detector.isOffRoute(latLng(0.0, 0.6), route)

        verify(exactly = 1) {
            distanceCalculator.resetCache()
        }
    }

    @Test
    fun `should return false when distance equals threshold`() {
        every {
            distanceCalculator.distanceFromPolyline(any(), any())
        } returns 15.0

        val result = detector.isOffRoute(latLng(0.0, 0.5), route)

        assertFalse(result)
    }

    @Test
    fun `should handle fluctuating distances correctly`() {
        every {
            distanceCalculator.distanceFromPolyline(any(), any())
        } returnsMany listOf(10.0, 20.0, 10.0, 20.0)

        val location = latLng(0.0, 0.5)

        val r1 = detector.isOffRoute(location, route) // false
        val r2 = detector.isOffRoute(location, route) // pending off-route
        val r3 = detector.isOffRoute(location, route) // pending reset
        nowMillis = 1500L
        val r4 = detector.isOffRoute(location, route) // new pending off-route

        assertFalse(r1)
        assertFalse(r2)
        assertFalse(r3)
        assertFalse(r4)
    }

    @Test
    fun `should reset confirmed off route state when route changes`() {
        every {
            distanceCalculator.distanceFromPolyline(any(), any())
        } returns 20.0

        val route1 = listOf(latLng(0.0, 0.0), latLng(0.0, 1.0))
        val route2 = listOf(latLng(1.0, 1.0), latLng(2.0, 2.0))
        val location = latLng(1.0, 1.0)

        detector.isOffRoute(location, route1)
        nowMillis = 1500L
        val confirmedOffRoute = detector.isOffRoute(location, route1)
        val resetForNewRoute = detector.isOffRoute(location, route2)

        assertTrue(confirmedOffRoute)
        assertFalse(resetForNewRoute)
    }

}
