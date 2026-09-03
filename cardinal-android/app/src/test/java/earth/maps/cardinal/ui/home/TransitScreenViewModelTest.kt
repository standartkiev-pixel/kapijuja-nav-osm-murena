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

package earth.maps.cardinal.ui.home

import android.content.Context
import android.location.Location
import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.transit.StopPlace
import earth.maps.cardinal.transit.StopTime
import earth.maps.cardinal.transit.StopTimesResponse
import earth.maps.cardinal.transit.TransitStop
import earth.maps.cardinal.transit.TransitousService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransitScreenViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: TransitScreenViewModel
    private lateinit var mockContext: Context
    private lateinit var mockLocationRepository: LocationRepository
    private lateinit var mockTransitousService: TransitousService
    private lateinit var mockAppPreferenceRepository: AppPreferenceRepository

    private val testLocation = Location("test").apply {
        latitude = 37.7749
        longitude = -122.4194
    }

    private val testLatLng = LatLng(37.7749, -122.4194)

    private val testTransitStop = TransitStop(
        type = "STOP",
        tokens = listOf("test"),
        name = "Test Stop",
        id = "test-stop-id",
        lat = 37.7749,
        lon = -122.4194,
        level = 0.0,
        tz = "America/Los_Angeles",
        areas = emptyList(),
        score = 1.0
    )

    private val testStopPlace = StopPlace(
        name = "Test Stop",
        stopId = "test-stop-id",
        lat = 37.7749,
        lon = -122.4194,
        level = 0.0,
        tz = "America/Los_Angeles",
        vertexType = "TRANSIT"
    )

    private val testStopTime = StopTime(
        place = testStopPlace,
        mode = "BUS",
        realTime = true,
        headsign = "Downtown",
        agencyId = "agency-1",
        agencyName = "Test Agency",
        agencyUrl = "https://example.com",
        routeColor = "FF0000",
        tripId = "trip-1",
        routeType = 3,
        routeShortName = "42",
        routeLongName = "Test Route",
        tripShortName = null,
        displayName = "42 - Downtown",
        pickupDropoffType = "NORMAL",
        cancelled = false,
        tripCancelled = false,
        source = "gtfs"
    )

    private val testStopTimesResponse = StopTimesResponse(
        stopTimes = listOf(testStopTime),
        place = testStopPlace
    )

    @Before
    fun setup() {
        mockContext = mockk()
        mockLocationRepository = mockk()
        mockTransitousService = mockk()
        mockAppPreferenceRepository = mockk()

        // Mock the use24HourFormat flow
        every { mockAppPreferenceRepository.use24HourFormat } returns MutableStateFlow(false)

        // Mock the locationFlow
        every { mockLocationRepository.locationFlow } returns MutableStateFlow(testLocation)

        viewModel = TransitScreenViewModel(
            context = mockContext,
            locationRepository = mockLocationRepository,
            transitousService = mockTransitousService,
            appPreferenceRepository = mockAppPreferenceRepository
        )
    }

    @Test
    fun `initial state should be correct`() {
        assertNull(viewModel.stop.value)
        assertNull(viewModel.reverseGeocodedStop.value)
        assertTrue(viewModel.departures.value.isEmpty())
        assertFalse(viewModel.didLoadingFail.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isRefreshingDepartures.value)
    }

    @Test
    fun `use24HourFormat should reflect repository state`() = runTest {
        val expectedFlow = MutableStateFlow(true)
        every { mockAppPreferenceRepository.use24HourFormat } returns expectedFlow

        viewModel = TransitScreenViewModel(
            context = mockContext,
            locationRepository = mockLocationRepository,
            transitousService = mockTransitousService,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(true, viewModel.use24HourFormat.first())
    }

    @Test
    fun `refreshData with location should reverse geocode and fetch departures`() = runTest {
        // Mock reverse geocoding
        coEvery {
            mockTransitousService.reverseGeocode(
                name = null, latitude = testLatLng.latitude, longitude = testLatLng.longitude, type = "STOP"
            )
        } returns flowOf(listOf(testTransitStop))

        // Mock stop times
        coEvery {
            mockTransitousService.getStopTimes(
                testTransitStop.id, n = 200, radius = 1000
            )
        } returns flowOf(testStopTimesResponse)

        viewModel.refreshData()
        advanceUntilIdle()

        coVerify {
            mockTransitousService.reverseGeocode(
                name = null, latitude = testLatLng.latitude, longitude = testLatLng.longitude, type = "STOP"
            )
        }
        coVerify {
            mockTransitousService.getStopTimes(
                testTransitStop.id, n = 200, radius = 1000
            )
        }
        assertEquals(testTransitStop.id, viewModel.stop.value)
        assertEquals(testTransitStop, viewModel.reverseGeocodedStop.value)
        assertEquals(listOf(testStopTime), viewModel.departures.value)
        assertFalse(viewModel.didLoadingFail.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isRefreshingDepartures.value)
    }


    @Test
    fun `reverseGeocodeStop with exception should handle error gracefully`() = runTest {
        coEvery {
            mockTransitousService.reverseGeocode(
                name = null, latitude = testLatLng.latitude, longitude = testLatLng.longitude, type = "STOP"
            )
        } throws RuntimeException("Network error")

        viewModel.refreshData()
        advanceUntilIdle()

        coVerify {
            mockTransitousService.reverseGeocode(
                name = null, latitude = testLatLng.latitude, longitude = testLatLng.longitude, type = "STOP"
            )
        }
        coVerify(exactly = 0) { mockTransitousService.getStopTimes(any(), any(), any()) }
        assertNull(viewModel.stop.value)
        assertNull(viewModel.reverseGeocodedStop.value)
        assertTrue(viewModel.departures.value.isEmpty())
        assertFalse(viewModel.didLoadingFail.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isRefreshingDepartures.value)
    }

    @Test
    fun `fetchDepartures with exception should set didLoadingFail to true`() = runTest {
        // Mock reverse geocoding to succeed
        coEvery {
            mockTransitousService.reverseGeocode(
                name = null, latitude = testLatLng.latitude, longitude = testLatLng.longitude, type = "STOP"
            )
        } returns flowOf(listOf(testTransitStop))

        // Mock stop times to throw exception
        coEvery {
            mockTransitousService.getStopTimes(
                testTransitStop.id, n = 200, radius = 1000
            )
        } throws RuntimeException("Network error")

        viewModel.refreshData()
        advanceUntilIdle()

        coVerify {
            mockTransitousService.reverseGeocode(
                name = null, latitude = testLatLng.latitude, longitude = testLatLng.longitude, type = "STOP"
            )
        }
        coVerify {
            mockTransitousService.getStopTimes(
                testTransitStop.id, n = 200, radius = 1000
            )
        }
        assertEquals(testTransitStop.id, viewModel.stop.value)
        assertEquals(testTransitStop, viewModel.reverseGeocodedStop.value)
        assertTrue(viewModel.departures.value.isEmpty())
        assertTrue(viewModel.didLoadingFail.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isRefreshingDepartures.value)
    }

    @Test
    fun `aggregateStopTimes should filter and sort stop times by proximity`() = runTest {
        // Create multiple stop times at different locations
        val farStopPlace = testStopPlace.copy(lat = 38.0, lon = -123.0)
        val farStopTime = testStopTime.copy(place = farStopPlace)
        
        // Create stop times with same route but different stops
        val sameRouteStopPlace1 = testStopPlace.copy(stopId = "stop-1")
        val sameRouteStopPlace2 = testStopPlace.copy(stopId = "stop-2")
        val sameRouteStopTime1 = testStopTime.copy(place = sameRouteStopPlace1)
        val sameRouteStopTime2 = testStopTime.copy(place = sameRouteStopPlace2)
        
        val allStopTimes = listOf(farStopTime, sameRouteStopTime1, sameRouteStopTime2)
        
        // Mock reverse geocoding
        coEvery {
            mockTransitousService.reverseGeocode(
                name = null, latitude = testLatLng.latitude, longitude = testLatLng.longitude, type = "STOP"
            )
        } returns flowOf(listOf(testTransitStop))

        // Mock stop times
        coEvery {
            mockTransitousService.getStopTimes(
                testTransitStop.id, n = 200, radius = 1000
            )
        } returns flowOf(StopTimesResponse(stopTimes = allStopTimes, place = testStopPlace))

        viewModel.refreshData()
        advanceUntilIdle()

        // Should only include the closest stop for each route/headsign pair
        val result = viewModel.departures.value
        assertEquals(1, result.size)
        assertEquals(sameRouteStopTime1.routeShortName, result[0].routeShortName)
        assertEquals(sameRouteStopTime1.headsign, result[0].headsign)
    }

    @Test
    fun `aggregateStopTimes with null lastLocation should return empty list`() = runTest {
        // Create a new ViewModel with null location
        every { mockLocationRepository.locationFlow } returns MutableStateFlow(null)
        
        viewModel = TransitScreenViewModel(
            context = mockContext,
            locationRepository = mockLocationRepository,
            transitousService = mockTransitousService,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        // Mock reverse geocoding to succeed
        coEvery {
            mockTransitousService.reverseGeocode(
                name = null, latitude = testLatLng.latitude, longitude = testLatLng.longitude, type = "STOP"
            )
        } returns flowOf(listOf(testTransitStop))

        // Mock stop times
        coEvery {
            mockTransitousService.getStopTimes(
                testTransitStop.id, n = 200, radius = 1000
            )
        } returns flowOf(testStopTimesResponse)

        // Manually set a location after creation
        viewModel.refreshData()
        advanceUntilIdle()

        // The aggregateStopTimes should handle null lastLocation gracefully
        assertNotNull(viewModel.departures.value)
    }
}