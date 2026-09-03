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

import androidx.compose.ui.text.input.TextFieldValue
import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.ViewportRepository
import earth.maps.cardinal.data.room.RecentSearch
import earth.maps.cardinal.data.room.RecentSearchRepository
import earth.maps.cardinal.data.room.SavedPlaceRepository
import earth.maps.cardinal.geocoding.GeocodingService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: HomeViewModel
    private lateinit var mockGeocodingService: GeocodingService
    private lateinit var mockViewportRepository: ViewportRepository
    private lateinit var mockSavedPlaceRepository: SavedPlaceRepository
    private lateinit var mockRecentSearchRepository: RecentSearchRepository

    private val testPlace = Place(
        id = "test-place-id",
        name = "Test Place",
        latLng = LatLng(37.7749, -122.4194)
    )

    private val testRecentSearch = RecentSearch(
        id = "test-recent-search-id",
        name = "Test Recent Search",
        description = "place",
        icon = "place",
        latitude = 37.7749,
        longitude = -122.4194,
        tappedAt = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        mockGeocodingService = mockk()
        mockViewportRepository = mockk()
        mockSavedPlaceRepository = mockk()
        mockRecentSearchRepository = mockk()

        // Mock default flows
        every { mockRecentSearchRepository.getRecentSearches() } returns emptyFlow()
        every { mockViewportRepository.viewportCenter } returns MutableStateFlow(LatLng(37.7749, -122.4194))
        every { mockSavedPlaceRepository.toPlace(any()) } returns testPlace
        every { mockSavedPlaceRepository.getPinnedPlacesAsFlow() } returns MutableStateFlow(emptyList())
        coEvery { mockSavedPlaceRepository.gePinnedPlacesForSearch() } returns emptyList()
        every { mockRecentSearchRepository.toPlace(any()) } returns testPlace

        viewModel = HomeViewModel(
            geocodingService = mockGeocodingService,
            viewportRepository = mockViewportRepository,
            savedPlaceRepository = mockSavedPlaceRepository,
            recentSearchRepository = mockRecentSearchRepository
        )
    }

    @Test
    fun `initial state should be correct`() {
        assertEquals(TextFieldValue(), viewModel.searchQueryValue)
        assertEquals(emptyList<Place>(), viewModel.geocodeResults.value)
        assertFalse(viewModel.isSearching)
        assertNull(viewModel.searchError)
    }

    @Test
    fun `updateSearchQuery should update searchQuery and searchQueryFlow`() {
        val newQuery = TextFieldValue("Test Query")
        
        viewModel.updateSearchQuery(newQuery)
        
        assertEquals(newQuery, viewModel.searchQueryValue)
    }

    @Test
    fun `updateSearchQuery with empty query should clear results`() = runTest {
        // First set a non-empty query
        viewModel.updateSearchQuery(TextFieldValue("Test"))
        advanceUntilIdle()
        
        // Then set empty query
        viewModel.updateSearchQuery(TextFieldValue(""))
        advanceUntilIdle()
        
        assertEquals(emptyList<Place>(), viewModel.geocodeResults.value)
        assertNull(viewModel.searchError)
    }

    @Test
    fun `performSearch should update geocodeResults on success`() = runTest {
        val query = "Test Query"
        val expectedResults = listOf(testPlace)
        
        coEvery { 
            mockGeocodingService.geocode(query, any()) 
        } returns expectedResults
        
        viewModel.updateSearchQuery(TextFieldValue(query))
        advanceUntilIdle()
        
        assertEquals(expectedResults, viewModel.geocodeResults.value)
        assertFalse(viewModel.isSearching)
        assertNull(viewModel.searchError)
    }

    @Test
    fun `performSearch should prioritize matching saved places`() = runTest {
        val query = "Thane"
        val savedPlace = Place(
            id = "saved-cosmos-bank",
            name = "The Cosmos Bank",
            address = earth.maps.cardinal.data.Address(city = "Thane"),
            latLng = LatLng(37.7749, -122.4194)
        )
        val otherGeocodePlace = Place(
            id = "geocode-thane-city",
            name = "Thane",
            latLng = LatLng(37.7849, -122.4094)
        )

        coEvery { mockSavedPlaceRepository.gePinnedPlacesForSearch() } returns listOf(savedPlace)
        coEvery {
            mockGeocodingService.geocode(query, any())
        } returns listOf(otherGeocodePlace)

        viewModel.updateSearchQuery(TextFieldValue(query))
        advanceUntilIdle()

        assertEquals(listOf(savedPlace, otherGeocodePlace), viewModel.geocodeResults.value)
    }

    @Test
    fun `performSearch should handle error and set searchError`() = runTest {
        val query = "Test Query"
        val errorMessage = "Search failed"
        
        coEvery { 
            mockGeocodingService.geocode(query, any()) 
        } throws Exception(errorMessage)
        
        viewModel.updateSearchQuery(TextFieldValue(query))
        advanceUntilIdle()
        
        assertEquals(emptyList<Place>(), viewModel.geocodeResults.value)
        assertFalse(viewModel.isSearching)
        assertEquals(errorMessage, viewModel.searchError)
    }

    @Test
    fun `pinnedPlaces should return only pinned places`() = runTest {
        every { mockSavedPlaceRepository.getPinnedPlacesAsFlow() } returns MutableStateFlow(
            listOf(testPlace)
        )
        
        val result = viewModel.pinnedPlaces()
        
        // Since it returns a Flow, we need to collect it with take(1)
        val collectedResult = result.take(1).toList()
        
        assertEquals(1, collectedResult.size)
        assertEquals(1, collectedResult[0].size)
        assertEquals(testPlace, collectedResult[0][0])
    }

    @Test
    fun `expandSearch should set searchExpanded to true`() = runTest {
        // Collect the flow with take(1) after the action
        viewModel.expandSearch()
        
        val collectedResult = viewModel.searchExpanded.take(1).toList()
        
        assertEquals(1, collectedResult.size)
        assertTrue(collectedResult[0])
    }

    @Test
    fun `collapseSearch should set searchExpanded to false`() = runTest {
        // First expand
        viewModel.expandSearch()
        
        // Then collapse
        viewModel.collapseSearch()
        
        val collectedResult = viewModel.searchExpanded.take(1).toList()
        
        assertEquals(1, collectedResult.size)
        assertFalse(collectedResult[0])
    }

    @Test
    fun `onPlaceSelected should add to recent searches`() = runTest {
        coEvery { mockRecentSearchRepository.addRecentSearch(testPlace) } returns Result.success(Unit)
        
        viewModel.onPlaceSelected(testPlace)
        advanceUntilIdle()
        
        coVerify { mockRecentSearchRepository.addRecentSearch(testPlace) }
    }

    @Test
    fun `recentSearches should return recent searches flow`() = runTest {
        val expectedRecentSearches = listOf(testRecentSearch)
        every { mockRecentSearchRepository.getRecentSearches() } returns MutableStateFlow(expectedRecentSearches)
        
        val result = viewModel.recentSearches()
        
        // Since it returns a Flow, we need to collect it with take(1)
        val collectedResult = result.take(1).toList()
        
        assertEquals(1, collectedResult.size)
        assertEquals(expectedRecentSearches, collectedResult[0])
    }

    @Test
    fun `searchToPlace should convert recent search to place`() {
        every { mockRecentSearchRepository.toPlace(testRecentSearch) } returns testPlace
        
        val result = viewModel.searchToPlace(testRecentSearch)
        
        assertEquals(testPlace, result)
    }

    @Test
    fun `removeRecentSearch should call repository removeRecentSearch`() = runTest {
        coEvery { mockRecentSearchRepository.removeRecentSearch(testRecentSearch) } returns Unit
        
        viewModel.removeRecentSearch(testRecentSearch)
        advanceUntilIdle()
        
        coVerify { mockRecentSearchRepository.removeRecentSearch(testRecentSearch) }
    }

    @Test
    fun `search should be debounced`() = runTest {
        val query1 = "Test1"
        val query2 = "Test2"
        val expectedResults = listOf(testPlace)
        
        coEvery { 
            mockGeocodingService.geocode(query2, any()) 
        } returns expectedResults
        
        // Update query twice quickly
        viewModel.updateSearchQuery(TextFieldValue(query1))
        viewModel.updateSearchQuery(TextFieldValue(query2))
        
        // Only the second query should trigger search due to debouncing
        advanceUntilIdle()
        
        coVerify { mockGeocodingService.geocode(query2, any()) }
        coVerify(inverse = true) { mockGeocodingService.geocode(query1, any()) }
        assertEquals(expectedResults, viewModel.geocodeResults.value)
    }
}
