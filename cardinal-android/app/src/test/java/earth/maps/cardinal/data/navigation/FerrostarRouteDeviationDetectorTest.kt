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
import earth.maps.cardinal.domain.DeviationDetector
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteDeviation
import uniffi.ferrostar.UserLocation

class FerrostarRouteDeviationDetectorTest {

    private val deviationDetector = mockk<DeviationDetector>()

    private lateinit var detector: FerrostarRouteDeviationDetector

    @Before
    fun setup() {
        detector = FerrostarRouteDeviationDetector(deviationDetector)
    }

    private fun latLng(lat: Double, lng: Double) = LatLng(lat, lng)

    @Test
    fun `should return NoDeviation when deviationDetector returns false`() {
        every {
            deviationDetector.isOffRoute(any(), any())
        } returns false

        val location = mockk<UserLocation> {
            every { coordinates.lat } returns 12.0
            every { coordinates.lng } returns 77.0
        }

        val route = mockk<Route> {
            every { geometry } returns listOf(
                mockk {
                    every { lat } returns 12.0
                    every { lng } returns 77.0
                }
            )
        }

        val result = detector.checkRouteDeviation(location, route, mockk())

        assertTrue(result is RouteDeviation.NoDeviation)
    }

    @Test
    fun `should return OffRoute when deviationDetector returns true`() {
        every {
            deviationDetector.isOffRoute(any(), any())
        } returns true

        val location = mockk<UserLocation> {
            every { coordinates.lat } returns 12.0
            every { coordinates.lng } returns 77.0
        }

        val route = mockk<Route> {
            every { geometry } returns listOf(
                mockk {
                    every { lat } returns 12.0
                    every { lng } returns 77.0
                }
            )
        }

        val result = detector.checkRouteDeviation(location, route, mockk())

        assertTrue(result is RouteDeviation.OffRoute)

        val offRoute = result as RouteDeviation.OffRoute
        assertEquals(30.0, offRoute.deviationFromRouteLine, 0.0)
    }

    @Test
    fun `should map UserLocation to LatLng correctly`() {
        val slotLocation = slot<LatLng>()

        every {
            deviationDetector.isOffRoute(capture(slotLocation), any())
        } returns false

        val location = mockk<UserLocation> {
            every { coordinates.lat } returns 10.5
            every { coordinates.lng } returns 20.5
        }

        val route = mockk<Route> {
            every { geometry } returns emptyList()
        }

        detector.checkRouteDeviation(location, route, mockk())

        assertEquals(10.5, slotLocation.captured.latitude, 0.0)
        assertEquals(20.5, slotLocation.captured.longitude, 0.0)
    }

    @Test
    fun `should map route geometry to LatLng list`() {
        val slotRoute = slot<List<LatLng>>()

        every {
            deviationDetector.isOffRoute(any(), capture(slotRoute))
        } returns false

        val route = mockk<Route> {
            every { geometry } returns listOf(
                mockk {
                    every { lat } returns 1.0
                    every { lng } returns 2.0
                },
                mockk {
                    every { lat } returns 3.0
                    every { lng } returns 4.0
                }
            )
        }

        val location = mockk<UserLocation> {
            every { coordinates.lat } returns 0.0
            every { coordinates.lng } returns 0.0
        }

        detector.checkRouteDeviation(location, route, mockk())

        val captured = slotRoute.captured

        assertEquals(2, captured.size)
        assertEquals(1.0, captured[0].latitude, 0.0)
        assertEquals(2.0, captured[0].longitude, 0.0)
        assertEquals(3.0, captured[1].latitude, 0.0)
        assertEquals(4.0, captured[1].longitude, 0.0)
    }

    @Test
    fun `should call deviationDetector once`() {
        every {
            deviationDetector.isOffRoute(any(), any())
        } returns false

        val location = mockk<UserLocation> {
            every { coordinates.lat } returns 0.0
            every { coordinates.lng } returns 0.0
        }

        val route = mockk<Route> {
            every { geometry } returns emptyList()
        }

        detector.checkRouteDeviation(location, route, mockk())

        verify(exactly = 1) {
            deviationDetector.isOffRoute(any(), any())
        }
    }

    @Test
    fun `should handle empty geometry`() {
        every {
            deviationDetector.isOffRoute(any(), any())
        } returns false

        val location = mockk<UserLocation> {
            every { coordinates.lat } returns 0.0
            every { coordinates.lng } returns 0.0
        }

        val route = mockk<Route> {
            every { geometry } returns emptyList()
        }

        val result = detector.checkRouteDeviation(location, route, mockk())

        assertTrue(result is RouteDeviation.NoDeviation)
    }

    @Test
    fun `should handle multiple geometry points when off route`() {
        every {
            deviationDetector.isOffRoute(any(), any())
        } returns true

        val location = mockk<UserLocation> {
            every { coordinates.lat } returns 5.0
            every { coordinates.lng } returns 5.0
        }

        val route = mockk<Route> {
            every { geometry } returns listOf(
                mockk { every { lat } returns 1.0; every { lng } returns 1.0 },
                mockk { every { lat } returns 2.0; every { lng } returns 2.0 },
                mockk { every { lat } returns 3.0; every { lng } returns 3.0 }
            )
        }

        val result = detector.checkRouteDeviation(location, route, mockk())

        assertTrue(result is RouteDeviation.OffRoute)
    }

}
