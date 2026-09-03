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

package earth.maps.cardinal.data.room

import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.Place
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecentSearchRepositoryTest {

    private lateinit var repository: RecentSearchRepository
    private val mockDatabase = mockk<AppDatabase>()
    private val mockSearchDao = mockk<RecentSearchDao>()

    @Before
    fun setup() {
        every { mockDatabase.recentSearchDao() } returns mockSearchDao
        repository = RecentSearchRepository(mockDatabase)
    }

    @Test
    fun `addRecentSearch should not insert my location places`() = runTest {
        val myLocationPlace = Place(
            name = "My Location",
            description = "Current location",
            icon = "location",
            latLng = LatLng(47.6205, -122.3493),
            isMyLocation = true
        )

        val result = repository.addRecentSearch(myLocationPlace)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { mockSearchDao.insertSearch(any()) }
        coVerify(exactly = 0) { mockSearchDao.deleteOldSearches(any()) }
    }

    @Test
    fun `addRecentSearch should insert normal places and trim old searches`() = runTest {
        coEvery { mockSearchDao.insertSearch(any()) } returns Unit
        coEvery { mockSearchDao.deleteOldSearches(RecentSearchRepository.MAX_RECENT_SEARCHES) } returns Unit
        val normalPlace = Place(
            name = "Rain City Fit",
            description = "515 Union Ave",
            icon = "search",
            latLng = LatLng(47.6071, -122.3298)
        )

        val result = repository.addRecentSearch(normalPlace)

        assertTrue(result.isSuccess)
        coVerify { mockSearchDao.insertSearch(match { it.name == normalPlace.name }) }
        coVerify { mockSearchDao.deleteOldSearches(RecentSearchRepository.MAX_RECENT_SEARCHES) }
    }

    @Test
    fun `getRecentSearches should deduplicate and limit recent searches`() = runTest {
        val normalSearch = recentSearch(
            id = "normal",
            name = "Ada's Technical Books and Cafe",
            description = "425 15th Ave East",
            icon = "search"
        )
        val duplicateSearch = normalSearch.copy(
            id = "duplicate",
            tappedAt = 2_000L
        )
        val otherSearch = recentSearch(
            id = "other",
            name = "Rain City Fit",
            description = "515 Union Ave",
            icon = "search"
        )
        every { mockSearchDao.getRecentSearches() } returns flowOf(
            listOf(normalSearch, duplicateSearch, otherSearch)
        )

        val result = repository.getRecentSearches(limit = 2).first()

        assertEquals(listOf(normalSearch, otherSearch), result)
    }

    private fun recentSearch(
        id: String,
        name: String,
        description: String,
        icon: String
    ): RecentSearch {
        return RecentSearch(
            id = id,
            name = name,
            description = description,
            icon = icon,
            latitude = 47.0,
            longitude = -122.0,
            tappedAt = 1_000L
        )
    }
}
