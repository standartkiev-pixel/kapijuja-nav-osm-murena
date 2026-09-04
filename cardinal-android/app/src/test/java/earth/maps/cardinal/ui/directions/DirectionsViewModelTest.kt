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

package earth.maps.cardinal.ui.directions

import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.PlanStateRepository
import earth.maps.cardinal.data.RouteState
import earth.maps.cardinal.data.RouteStateRepository
import earth.maps.cardinal.data.RoutingMode
import earth.maps.cardinal.data.RoutingProfileSelectionStore
import earth.maps.cardinal.data.TransitPlanState
import earth.maps.cardinal.data.ViewportRepository
import earth.maps.cardinal.data.room.RecentSearchRepository
import earth.maps.cardinal.data.room.RoutingProfileRepository
import earth.maps.cardinal.data.room.SavedPlaceDao
import earth.maps.cardinal.data.room.SavedPlaceRepository
import earth.maps.cardinal.geocoding.GeocodingService
import earth.maps.cardinal.network.HttpRequestException
import earth.maps.cardinal.network.HttpRequestFailure
import earth.maps.cardinal.routing.FerrostarWrapper
import earth.maps.cardinal.routing.FerrostarWrapperRepository
import earth.maps.cardinal.routing.RouteRepository
import earth.maps.cardinal.routing.TrafficRouteResult
import earth.maps.cardinal.transit.PlanResponse
import earth.maps.cardinal.transit.TransitPlace
import earth.maps.cardinal.transit.TransitousService
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uniffi.ferrostar.ParsingException
import uniffi.ferrostar.Route
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DirectionsViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private lateinit var viewModel: DirectionsViewModel

    private val mockGeocodingService = mockk<GeocodingService>()
    private val mockFerrostarWrapperRepository = mockk<FerrostarWrapperRepository>()
    private val mockViewportRepository = mockk<ViewportRepository>()
    private val mockPlaceDao = mockk<SavedPlaceDao>()
    private val mockSavedPlaceRepository = mockk<SavedPlaceRepository>()
    private val mockLocationRepository = mockk<LocationRepository>()
    private val mockRoutingProfileRepository = mockk<RoutingProfileRepository>()
    private val mockRoutingProfileSelectionStore = mockk<RoutingProfileSelectionStore>(relaxed = true)
    private val mockRouteRepository = mockk<RouteRepository>()
    private val mockAppPreferenceRepository = mockk<AppPreferenceRepository>()
    private val mockTransitousService = mockk<TransitousService>()
    private val mockRecentSearchRepository = mockk<RecentSearchRepository>()
    private val mockRouteStateRepository = mockk<RouteStateRepository>()
    private val mockPlanStateRepository = mockk<PlanStateRepository>()

    @Before
    fun setup() {
        // Mock saved places flow
        every { mockPlaceDao.getAllPlacesAsFlow() } returns flowOf(emptyList())
        every { mockViewportRepository.viewportCenter } returns MutableStateFlow(LatLng(37.7749, -122.4194))
        coEvery { mockSavedPlaceRepository.gePinnedPlacesForSearch() } returns emptyList()

        // Mock app preferences
        coEvery { mockAppPreferenceRepository.continuousLocationTracking } returns MutableStateFlow(
            false
        )
        coEvery { mockAppPreferenceRepository.lastRoutingMode } returns MutableStateFlow("auto")
        coEvery { mockAppPreferenceRepository.setLastRoutingMode(any()) } returns Unit

        // Mock route state repository
        coEvery { mockRouteStateRepository.routeState } returns MutableStateFlow(RouteState())
        coEvery { mockPlanStateRepository.planState } returns MutableStateFlow(TransitPlanState())
        coEvery { mockRouteStateRepository.clear() } returns Unit
        coEvery { mockPlanStateRepository.clear() } returns Unit
        coEvery { mockRouteStateRepository.setLoading(any()) } returns Unit
        coEvery { mockPlanStateRepository.setLoading(any()) } returns Unit
        every { mockRouteStateRepository.setRoutes(any(), any(), any(), any()) } returns Unit
        coEvery { mockPlanStateRepository.setPlanResponse(any()) } returns Unit
        every { mockPlanStateRepository.selectItinerary(any()) } returns Unit
        coEvery { mockRouteStateRepository.setError(any()) } returns Unit
        coEvery { mockRouteStateRepository.setDirectionError(any()) } returns Unit
        coEvery { mockPlanStateRepository.setError(any()) } returns Unit
        coEvery { mockPlanStateRepository.setDirectionError(any()) } returns Unit

        // Mock routing profile repository
        coEvery { mockRoutingProfileRepository.getDefaultProfile(any()) } returns Result.success(
            null
        )
        coEvery { mockRoutingProfileRepository.getProfilesForMode(any()) } returns flowOf(emptyList())

        // Mock FerrostarWrapperRepository
        coEvery { mockFerrostarWrapperRepository.awaitInitialization() } returns Unit
        coEvery { mockFerrostarWrapperRepository.resetOptionsToDefaultsForMode(any()) } returns Unit

        viewModel = DirectionsViewModel(
            geocodingService = mockGeocodingService,
            ferrostarWrapperRepository = mockFerrostarWrapperRepository,
            viewportRepository = mockViewportRepository,
            placeDao = mockPlaceDao,
            savedPlaceRepository = mockSavedPlaceRepository,
            locationRepository = mockLocationRepository,
            routingProfileRepository = mockRoutingProfileRepository,
            routingProfileSelectionStore = mockRoutingProfileSelectionStore,
            routeRepository = mockRouteRepository,
            appPreferenceRepository = mockAppPreferenceRepository,
            transitousService = mockTransitousService,
            recentSearchRepository = mockRecentSearchRepository,
            routeStateRepository = mockRouteStateRepository,
            planStateRepository = mockPlanStateRepository,
        )
    }

    @Test
    fun `updateSearchQuery should update search query`() = runTest {
        val testQuery = "Test Search Query"

        viewModel.updateSearchQuery(testQuery)

        assertEquals(testQuery, viewModel.searchQuery)
    }

    @Test
    fun `updateSearchQuery with empty string should clear search query`() = runTest {
        val testQuery = "Test Search Query"
        viewModel.updateSearchQuery(testQuery)
        assertEquals(testQuery, viewModel.searchQuery)

        viewModel.updateSearchQuery("")
        assertEquals("", viewModel.searchQuery)
    }

    @Test
    fun `search should prioritize matching saved places`() = runTest {
        val query = "Thane"
        val savedPlace = Place(
            id = "saved-cosmos-bank",
            name = "The Cosmos Bank",
            address = earth.maps.cardinal.data.Address(city = "Thane"),
            latLng = LatLng(37.7749, -122.4194),
        )

        coEvery { mockSavedPlaceRepository.gePinnedPlacesForSearch() } returns listOf(savedPlace)
        coEvery {
            mockGeocodingService.geocode(query, any())
        } returns emptyList()

        viewModel.updateSearchQuery(query)
        advanceUntilIdle()

        assertEquals(listOf(savedPlace), viewModel.geocodeResults.value)
    }

    @Test
    fun `fetchDirectionsIfNeeded should set loading state for driving directions when both places are set`() =
        runTest {
            val fromPlace = Place(
                id = "1",
                name = "From Place",
                latLng = LatLng(0.0, 0.0),
                address = null
            )
            val toPlace = Place(
                id = "2",
                name = "To Place",
                latLng = LatLng(1.0, 1.0),
                address = null
            )

            val mockFerrostarWrapper = mockk<FerrostarWrapper>()
            coEvery { mockFerrostarWrapperRepository.driving } returns mockFerrostarWrapper
            every { mockFerrostarWrapper.etaCorrectionFactor } returns 1.15
            val mockRoute = mockk<Route>()
            coEvery {
                mockFerrostarWrapper.getRoutesWithNearestDestinationFirst(any(), any())
            } returns TrafficRouteResult(listOf(mockRoute), trafficAvailable = true)

            // Get initial state
            val initialState = viewModel.routeState.value
            assertEquals(RouteState(), initialState)
            assertFalse(initialState.isLoading)
            assertTrue(initialState.routes.isEmpty())

            // Trigger the state changes
            viewModel.updateFromPlace(fromPlace)
            viewModel.updateToPlace(toPlace)

            // Allow all coroutines, including viewModelScope, to complete
            advanceUntilIdle()

            // Verify repository interactions
            coVerify { mockRouteStateRepository.setLoading(true) }
            coVerify { mockRouteStateRepository.setRoutes(listOf(mockRoute), true, any(), 1.15) }
        }

    @Test
    fun `updateRoutingMode should update selected mode and save to preferences`() = runTest {
        val testMode = RoutingMode.PEDESTRIAN

        // Get initial mode
        val initialMode = viewModel.selectedRoutingMode
        assertEquals(RoutingMode.AUTO, initialMode)

        // Update the mode
        viewModel.updateRoutingMode(testMode)

        // Verify the mode was updated
        assertEquals(testMode, viewModel.selectedRoutingMode)

        // Verify it was saved to preferences
        coVerify { mockAppPreferenceRepository.setLastRoutingMode(testMode.value) }
    }

    @Test
    fun `selectTransitItinerary should update plan selection`() = runTest {
        viewModel.selectTransitItinerary(2)

        verify { mockPlanStateRepository.selectItinerary(2) }
    }

    @Test
    fun `updateToPlace with null should clear route and plan state`() = runTest {
        viewModel.updateToPlace(null)
        coVerify { mockRouteStateRepository.clear() }
        coVerify { mockPlanStateRepository.clear() }
    }

    @Test
    fun `updateFromPlace and updateToPlace should set places correctly`() = runTest {
        val fromPlace = Place(
            id = "from1",
            name = "From Place",
            latLng = LatLng(0.0, 0.0),
            address = null
        )
        val toPlace = Place(
            id = "to1",
            name = "To Place",
            latLng = LatLng(1.0, 1.0),
            address = null
        )

        // Initially, both places should be null
        assertNull(viewModel.fromPlace)
        assertNull(viewModel.toPlace)

        // Update from place
        viewModel.updateFromPlace(fromPlace)
        assertEquals(fromPlace, viewModel.fromPlace)
        assertNull(viewModel.toPlace) // toPlace should still be null

        // Update to place
        viewModel.updateToPlace(toPlace)
        assertEquals(fromPlace, viewModel.fromPlace)
        assertEquals(toPlace, viewModel.toPlace)

        // Setting fromPlace to null should not affect toPlace
        viewModel.updateFromPlace(null)
        assertNull(viewModel.fromPlace)
        assertEquals(toPlace, viewModel.toPlace)

        // Setting toPlace to null should not affect fromPlace
        viewModel.updateFromPlace(fromPlace)
        viewModel.updateToPlace(null)
        assertEquals(fromPlace, viewModel.fromPlace)
        assertNull(viewModel.toPlace)
    }

    @Test
    fun `fetchDirectionsIfNeeded should handle route fetching error`() = runTest {
        val fromPlace = Place(
            id = "1",
            name = "From Place",
            latLng = LatLng(0.0, 0.0),
            address = null
        )
        val toPlace = Place(
            id = "2",
            name = "To Place",
            latLng = LatLng(1.0, 1.0),
            address = null
        )

        mockFerrostarWrapperRepository.awaitInitialization()

        // Setup mock to throw an exception
        val mockFerrostarWrapper = mockk<FerrostarWrapper>()
        coEvery { mockFerrostarWrapperRepository.driving } returns mockFerrostarWrapper
        coEvery {
            mockFerrostarWrapper.getRoutesWithNearestDestinationFirst(any(), any())
        } throws Exception("Failed to get route")

        // Trigger the direction fetching
        viewModel.updateFromPlace(fromPlace)
        viewModel.updateToPlace(toPlace)

        // Allow coroutines to complete
        advanceUntilIdle()

        coVerify { mockRouteStateRepository.setLoading(true) }
        coVerify(timeout = 1_000) {
            mockRouteStateRepository.setDirectionError(DirectionUiError.Unknown)
        }
    }

    @Test
    fun `fetchDirectionsIfNeeded should show route not found when transit plan has no itineraries`() =
        runTest {
            val fromPlace = Place(
                id = "1",
                name = "From Place",
                latLng = LatLng(0.0, 0.0),
                address = null
            )
            val toPlace = Place(
                id = "2",
                name = "To Place",
                latLng = LatLng(1.0, 1.0),
                address = null
            )
            val emptyPlanResponse = PlanResponse(
                from = TransitPlace(
                    name = "From Place",
                    stopId = null,
                    lat = 0.0,
                    lon = 0.0,
                    level = 0.0
                ),
                to = TransitPlace(
                    name = "To Place",
                    stopId = null,
                    lat = 1.0,
                    lon = 1.0,
                    level = 0.0
                ),
                direct = emptyList(),
                itineraries = emptyList(),
                previousPageCursor = "",
                nextPageCursor = ""
            )

            every {
                mockTransitousService.getPlan(
                    from = any(),
                    to = any(),
                    withFares = true
                )
            } returns flowOf(emptyPlanResponse)

            viewModel.updateRoutingMode(RoutingMode.PUBLIC_TRANSPORT)
            viewModel.updateFromPlace(fromPlace)
            clearMocks(
                mockRouteStateRepository,
                mockPlanStateRepository,
                answers = false,
                recordedCalls = true,
                childMocks = false,
                verificationMarks = false
            )
            viewModel.updateToPlace(toPlace)

            advanceUntilIdle()

            coVerifyOrder {
                mockRouteStateRepository.clear()
                mockPlanStateRepository.clear()
                mockPlanStateRepository.setLoading(true)
            }
            coVerify { mockPlanStateRepository.setLoading(true) }
            coVerify { mockPlanStateRepository.setDirectionError(DirectionUiError.RouteNotFound) }
            coVerify(exactly = 0) { mockPlanStateRepository.setPlanResponse(emptyPlanResponse) }
        }

    @Test
    fun `fetchDirectionsIfNeeded should map transit plan exceptions to direction errors`() =
        runTest {
            val fromPlace = Place(
                id = "1",
                name = "From Place",
                latLng = LatLng(0.0, 0.0),
                address = null
            )
            val toPlace = Place(
                id = "2",
                name = "To Place",
                latLng = LatLng(1.0, 1.0),
                address = null
            )

            every {
                mockTransitousService.getPlan(
                    from = any(),
                    to = any(),
                    withFares = true
                )
            } returns flow {
                throw HttpRequestException(HttpRequestFailure.REQUEST_TIMEOUT)
            }

            viewModel.updateRoutingMode(RoutingMode.PUBLIC_TRANSPORT)
            viewModel.updateFromPlace(fromPlace)
            clearMocks(
                mockRouteStateRepository,
                mockPlanStateRepository,
                answers = false,
                recordedCalls = true,
                childMocks = false,
                verificationMarks = false
            )
            viewModel.updateToPlace(toPlace)

            advanceUntilIdle()

            coVerifyOrder {
                mockRouteStateRepository.clear()
                mockPlanStateRepository.clear()
                mockPlanStateRepository.setLoading(true)
            }
            coVerify { mockPlanStateRepository.setDirectionError(DirectionUiError.RequestTimedOut) }
            coVerify(exactly = 0) { mockPlanStateRepository.setError(any()) }
        }

    @Test
    fun `fetchDirectionsIfNeeded should hide status code errors from users`() = runTest {
        val fromPlace = Place(
            id = "1",
            name = "From Place",
            latLng = LatLng(0.0, 0.0),
            address = null
        )
        val toPlace = Place(
            id = "2",
            name = "To Place",
            latLng = LatLng(1.0, 1.0),
            address = null
        )

        val mockFerrostarWrapper = mockk<FerrostarWrapper>()
        coEvery { mockFerrostarWrapperRepository.driving } returns mockFerrostarWrapper
        coEvery {
            mockFerrostarWrapper.getRoutesWithNearestDestinationFirst(any(), any())
        } throws InvalidStatusCodeException(500)

        viewModel.updateFromPlace(fromPlace)
        viewModel.updateToPlace(toPlace)

        advanceUntilIdle()

        coVerify(timeout = 1_000) {
            mockRouteStateRepository.setDirectionError(DirectionUiError.ServerUnavailable)
        }
    }

    @Test
    fun `fetchDirectionsIfNeeded should handle too long error`() = runTest {
        val fromPlace = Place(
            id = "1",
            name = "New York",
            latLng = LatLng(40.7128, -74.0060),
            address = null
        )
        val toPlace = Place(
            id = "2",
            name = "London",
            latLng = LatLng(51.5074, -0.1278),
            address = null
        )

        val mockFerrostarWrapper = mockk<FerrostarWrapper>()
        coEvery { mockFerrostarWrapperRepository.driving } returns mockFerrostarWrapper

        val distanceExceededException = ParsingException.InvalidStatusCode(
            code = DirectionCodes.DISTANCE_EXCEEDED.value,
            description = "Distance exceeded"
        )
        coEvery {
            mockFerrostarWrapper.getRoutesWithNearestDestinationFirst(any(), any())
        } throws distanceExceededException

        val state = viewModel.routeState.value
        assertEquals(RouteState(), state)
        assertFalse(state.isLoading)
        assertTrue(state.routes.isEmpty())

        viewModel.updateFromPlace(fromPlace)
        viewModel.updateToPlace(toPlace)

        advanceUntilIdle()

        val updatedState = viewModel.routeState.value
        assertFalse(updatedState.isLoading)
        assertTrue(updatedState.routes.isEmpty())
        coVerifyOrder {
            mockRouteStateRepository.setLoading(true)
            mockRouteStateRepository.setDirectionError(DirectionUiError.DistanceExceeded)
        }
    }

    @Test
    fun `fetchDirectionsIfNeeded should not fetch if fromPlace is null`() = runTest {
        val toPlace = Place(
            id = "to1",
            name = "To Place",
            latLng = LatLng(1.0, 1.0),
            address = null
        )

        viewModel.updateFromPlace(null)
        viewModel.updateToPlace(toPlace)
        advanceUntilIdle()

        // Verify that no loading or route setting was called
        coVerify(exactly = 0) { mockRouteStateRepository.setLoading(any()) }
        coVerify(exactly = 0) { mockRouteStateRepository.setRoutes(any(), any(), any(), any()) }
        coVerify(exactly = 0) { mockRouteStateRepository.setError(any()) }
    }

    @Test
    fun `fetchDirectionsIfNeeded should not fetch if toPlace is null`() = runTest {
        val fromPlace = Place(
            id = "from1",
            name = "From Place",
            latLng = LatLng(0.0, 0.0),
            address = null
        )

        viewModel.updateFromPlace(fromPlace)
        viewModel.updateToPlace(null)
        advanceUntilIdle()

        // Verify that no loading or route setting was called
        coVerify(exactly = 0) { mockRouteStateRepository.setLoading(any()) }
        coVerify(exactly = 0) { mockRouteStateRepository.setRoutes(any(), any(), any(), any()) }
        coVerify(exactly = 0) { mockRouteStateRepository.setError(any()) }
    }
}
