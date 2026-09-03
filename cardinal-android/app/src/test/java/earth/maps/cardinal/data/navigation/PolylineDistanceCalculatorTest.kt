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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PolylineDistanceCalculatorTest {

    private lateinit var calculator: PolylineDistanceCalculator

    @Before
    fun setup() {
        calculator = PolylineDistanceCalculator()
    }

    // Helper
    private fun latLng(lat: Double, lon: Double) = LatLng(lat, lon)

    @Test
    fun `should return MAX_VALUE when polyline has less than 2 points`() {
        val point = latLng(0.0, 0.0)

        val result = calculator.distanceFromPolyline(point, emptyList())

        assertEquals(Double.MAX_VALUE, result, 0.0)
    }

    @Test
    fun `should return near zero when point lies on segment`() {
        val polyline = listOf(
            latLng(0.0, 0.0),
            latLng(0.0, 1.0)
        )

        val point = latLng(0.0, 0.5)

        val distance = calculator.distanceFromPolyline(point, polyline)

        assertTrue(distance < 1.0) // < 1 meter
    }

    @Test
    fun `should calculate small distance when point is near segment`() {
        val polyline = listOf(
            latLng(0.0, 0.0),
            latLng(0.0, 1.0)
        )

        val point = latLng(0.0001, 0.5) // ~11m away

        val distance = calculator.distanceFromPolyline(point, polyline)

        assertTrue(distance in 5.0..20.0)
    }

    @Test
    fun `should return large distance when point is far`() {
        val polyline = listOf(
            latLng(0.0, 0.0),
            latLng(0.0, 1.0)
        )

        val point = latLng(1.0, 1.0) // ~100km away

        val distance = calculator.distanceFromPolyline(point, polyline)

        assertTrue(distance > 100_000)
    }

    @Test
    fun `should early exit when very close to segment`() {
        val polyline = listOf(
            latLng(0.0, 0.0),
            latLng(0.0, 1.0),
            latLng(1.0, 1.0) // extra segment
        )

        val point = latLng(0.0, 0.1)

        val distance = calculator.distanceFromPolyline(point, polyline)

        assertTrue(distance < 5.0)
    }

    @Test
    fun `should reuse last segment index for nearby points`() {
        val polyline = listOf(
            latLng(0.0, 0.0),
            latLng(0.0, 1.0),
            latLng(0.0, 2.0)
        )

        val point1 = latLng(0.0, 0.4)
        val point2 = latLng(0.0, 0.5)

        val d1 = calculator.distanceFromPolyline(point1, polyline)
        val d2 = calculator.distanceFromPolyline(point2, polyline)

        assertTrue(d1 < 5.0)
        assertTrue(d2 < 5.0)
    }

    @Test
    fun `resetCache should reset segment index`() {
        val polyline = listOf(
            latLng(0.0, 0.0),
            latLng(0.0, 1.0)
        )

        val point = latLng(0.0, 0.5)

        calculator.distanceFromPolyline(point, polyline)

        calculator.resetCache()

        val distance = calculator.distanceFromPolyline(point, polyline)

        assertTrue(distance < 5.0)
    }

    @Test
    fun `should handle degenerate segment safely`() {
        val polyline = listOf(
            latLng(0.0, 0.0),
            latLng(0.0, 0.0) // same point
        )

        val point = latLng(0.1, 0.1)

        val distance = calculator.distanceFromPolyline(point, polyline)

        assertTrue(distance > 0)
    }

    @Test
    fun `should pick closest segment among multiple`() {
        val polyline = listOf(
            latLng(0.0, 0.0),
            latLng(0.0, 1.0),
            latLng(1.0, 1.0)
        )

        val point = latLng(0.0001, 0.8)

        val distance = calculator.distanceFromPolyline(point, polyline)

        assertTrue(distance < 20.0)
    }
}
