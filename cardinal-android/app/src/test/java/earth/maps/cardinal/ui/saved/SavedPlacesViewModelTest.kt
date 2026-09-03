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

package earth.maps.cardinal.ui.saved

import earth.maps.cardinal.MainCoroutineRule
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.room.SavedPlace
import earth.maps.cardinal.data.room.SavedPlaceDao
import earth.maps.cardinal.data.room.SavedPlaceRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SavedPlacesViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var mainCoroutineRule = MainCoroutineRule()

    private lateinit var viewModel: SavedPlacesViewModel
    private lateinit var mockSavedPlaceDao: SavedPlaceDao
    private lateinit var mockSavedPlaceRepository: SavedPlaceRepository

    private val testSavedPlace = SavedPlace(
        id = "test-saved-place-id",
        placeId = null,
        name = "Test Saved Place",
        type = "place",
        icon = "place",
        latitude = 37.7749,
        longitude = -122.4194,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        isPinned = true
    )

    private val testPlace = Place(
        id = "test-place-id",
        name = "Test Place",
        latLng = LatLng(37.7749, -122.4194)
    )

    @Before
    fun setup() {
        mockSavedPlaceDao = mockk()
        mockSavedPlaceRepository = mockk()

        // Mock default flows
        every { mockSavedPlaceDao.getAllPlacesAsFlow() } returns emptyFlow()
        every { mockSavedPlaceRepository.toPlace(any()) } returns testPlace

        viewModel = SavedPlacesViewModel(
            savedPlaceDao = mockSavedPlaceDao,
            savedPlaceRepository = mockSavedPlaceRepository
        )
    }

    @Test
    fun `observeAllPlaces should return flow from DAO`() = runTest {
        val expectedPlaces = listOf(testSavedPlace)
        every { mockSavedPlaceDao.getAllPlacesAsFlow() } returns MutableStateFlow(expectedPlaces)

        val result = viewModel.observeAllPlaces()
        val collectedResult = result.take(1).toList()

        assertEquals(1, collectedResult.size)
        assertEquals(expectedPlaces, collectedResult[0])
    }

    @Test
    fun `convertToPlace should call repository toPlace`() {
        every { mockSavedPlaceRepository.toPlace(testSavedPlace) } returns testPlace

        val result = viewModel.convertToPlace(testSavedPlace)

        assertEquals(testPlace, result)
    }

    @Test
    fun `convertToPlace should handle different saved places`() {
        val anotherSavedPlace = testSavedPlace.copy(id = "another-id", name = "Another Place")
        val anotherPlace = testPlace.copy(id = "another-id", name = "Another Place")
        
        every { mockSavedPlaceRepository.toPlace(anotherSavedPlace) } returns anotherPlace

        val result = viewModel.convertToPlace(anotherSavedPlace)

        assertEquals(anotherPlace, result)
    }
}