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

package earth.maps.cardinal.ui.place

import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.room.SavedPlace
import earth.maps.cardinal.data.room.SavedPlaceDao
import earth.maps.cardinal.transit.StopPlace
import earth.maps.cardinal.transit.StopTime
import earth.maps.cardinal.transit.StopTimesResponse
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
class TransitStopCardViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: TransitStopCardViewModel
    private lateinit var mockPlaceDao: SavedPlaceDao
    private lateinit var mockTransitousService: TransitousService
    private lateinit var mockAppPreferenceRepository: AppPreferenceRepository

    private val testPlace = Place(
        id = "test-place-id",
        name = "Test Transit Stop",
        description = "Test Description",
        latLng = LatLng(37.7749, -122.4194),
        isTransitStop = true,
        transitStopId = "test-stop-id"
    )

    private val testSavedPlace = SavedPlace(
        id = "test-saved-place-id",
        placeId = null,
        name = "Test Transit Stop",
        type = "transit_stop",
        icon = "transit",
        latitude = 37.7749,
        longitude = -122.4194,
        isTransitStop = true,
        transitStopId = "test-stop-id",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
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
        mockPlaceDao = mockk()
        mockTransitousService = mockk()
        mockAppPreferenceRepository = mockk()

        // Mock the use24HourFormat flow
        every { mockAppPreferenceRepository.use24HourFormat } returns MutableStateFlow(false)

        viewModel = TransitStopCardViewModel(
            placeDao = mockPlaceDao,
            transitousService = mockTransitousService,
            appPreferenceRepository = mockAppPreferenceRepository
        )
    }

    @Test
    fun `initial state should be correct`() {
        assertFalse(viewModel.isPlaceSaved.value)
        assertNull(viewModel.stop.value)
        assertTrue(viewModel.departures.value.isEmpty())
        assertFalse(viewModel.didLoadingFail.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isRefreshingDepartures.value)
    }

    @Test
    fun `setStop should update stop value`() {
        viewModel.setStop(testPlace)

        assertEquals(testPlace, viewModel.stop.value)
    }

    @Test
    fun `checkIfPlaceIsSaved with existing place should update isPlaceSaved to true`() = runTest {
        coEvery { mockPlaceDao.getPlace(testPlace.id!!) } returns testSavedPlace

        viewModel.checkIfPlaceIsSaved(testPlace)
        advanceUntilIdle()

        assertTrue(viewModel.isPlaceSaved.value)
        coVerify { mockPlaceDao.getPlace(testPlace.id!!) }
    }

    @Test
    fun `checkIfPlaceIsSaved with non-existing place should update isPlaceSaved to false`() = runTest {
        coEvery { mockPlaceDao.getPlace(testPlace.id!!) } returns null

        viewModel.checkIfPlaceIsSaved(testPlace)
        advanceUntilIdle()

        assertFalse(viewModel.isPlaceSaved.value)
        coVerify { mockPlaceDao.getPlace(testPlace.id!!) }
    }

    @Test
    fun `checkIfPlaceIsSaved with null place ID should not call repository`() = runTest {
        val placeWithoutId = testPlace.copy(id = null)

        viewModel.checkIfPlaceIsSaved(placeWithoutId)
        advanceUntilIdle()

        coVerify(exactly = 0) { mockPlaceDao.getPlace(any()) }
        assertFalse(viewModel.isPlaceSaved.value)
    }

    @Test
    fun `initializeDepartures with valid stop should check if place is saved and fetch departures`() = runTest {
        // Mock the getStopTimes flow
        coEvery {
            mockTransitousService.getStopTimes(testPlace.transitStopId!!)
        } returns flowOf(testStopTimesResponse)

        // Mock the placeDao.getPlace
        coEvery { mockPlaceDao.getPlace(testPlace.id!!) } returns testSavedPlace

        viewModel.setStop(testPlace)
        viewModel.initializeDepartures()
        advanceUntilIdle()

        coVerify { mockPlaceDao.getPlace(testPlace.id!!) }
        coVerify { mockTransitousService.getStopTimes(testPlace.transitStopId!!) }
        assertTrue(viewModel.isPlaceSaved.value)
        assertEquals(listOf(testStopTime), viewModel.departures.value)
        assertFalse(viewModel.didLoadingFail.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isRefreshingDepartures.value)
    }

    @Test
    fun `initializeDepartures with null stop should log error and not fetch departures`() = runTest {
        viewModel.initializeDepartures()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockPlaceDao.getPlace(any()) }
        coVerify(exactly = 0) { mockTransitousService.getStopTimes(any()) }
        assertTrue(viewModel.departures.value.isEmpty())
    }

    @Test
    fun `fetchDepartures with valid transit stop ID should update departures`() = runTest {
        coEvery {
            mockTransitousService.getStopTimes(testPlace.transitStopId!!)
        } returns flowOf(testStopTimesResponse)
        // Mock the placeDao.getPlace call
        coEvery { mockPlaceDao.getPlace(testPlace.id!!) } returns null

        viewModel.setStop(testPlace)
        viewModel.initializeDepartures()
        advanceUntilIdle()

        assertEquals(listOf(testStopTime), viewModel.departures.value)
        assertFalse(viewModel.didLoadingFail.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isRefreshingDepartures.value)
    }

    @Test
    fun `fetchDepartures with empty transit stop ID should not update departures`() = runTest {
        val placeWithoutTransitId = testPlace.copy(transitStopId = "")
        // Mock the placeDao.getPlace call
        coEvery { mockPlaceDao.getPlace(placeWithoutTransitId.id!!) } returns null
        
        viewModel.setStop(placeWithoutTransitId)
        viewModel.initializeDepartures()
        advanceUntilIdle()

        assertTrue(viewModel.departures.value.isEmpty())
        assertFalse(viewModel.didLoadingFail.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isRefreshingDepartures.value)
    }

    @Test
    fun `fetchDepartures with exception should set didLoadingFail to true`() = runTest {
        coEvery {
            mockTransitousService.getStopTimes(testPlace.transitStopId!!)
        } throws RuntimeException("Network error")
        // Mock the placeDao.getPlace call
        coEvery { mockPlaceDao.getPlace(testPlace.id!!) } returns null

        viewModel.setStop(testPlace)
        viewModel.initializeDepartures()
        advanceUntilIdle()

        assertTrue(viewModel.departures.value.isEmpty())
        assertTrue(viewModel.didLoadingFail.value)
        assertFalse(viewModel.isLoading.value)
        assertFalse(viewModel.isRefreshingDepartures.value)
    }

    @Test
    fun `refreshDepartures should set isRefreshingDepartures during refresh`() = runTest {
        // Mock the placeDao.getPlace call
        coEvery { mockPlaceDao.getPlace(testPlace.id!!) } returns null
        
        // Create a flow that emits once and completes
        coEvery {
            mockTransitousService.getStopTimes(testPlace.transitStopId!!)
        } returns flowOf(testStopTimesResponse)

        viewModel.setStop(testPlace)
        
        // Start refresh
        viewModel.refreshDepartures()
        
        // Complete refresh
        advanceUntilIdle()
        assertFalse(viewModel.isRefreshingDepartures.value)
        
        coVerify { mockTransitousService.getStopTimes(testPlace.transitStopId!!) }
    }

    @Test
    fun `refreshDepartures with null stop should not call service`() = runTest {
        viewModel.refreshDepartures()
        advanceUntilIdle()

        coVerify(exactly = 0) { mockTransitousService.getStopTimes(any()) }
        assertFalse(viewModel.isRefreshingDepartures.value)
    }

    @Test
    fun `use24HourFormat should reflect repository state`() = runTest {
        val expectedFlow = MutableStateFlow(true)
        every { mockAppPreferenceRepository.use24HourFormat } returns expectedFlow

        viewModel = TransitStopCardViewModel(
            placeDao = mockPlaceDao,
            transitousService = mockTransitousService,
            appPreferenceRepository = mockAppPreferenceRepository
        )

        assertEquals(true, viewModel.use24HourFormat.first())
    }
}