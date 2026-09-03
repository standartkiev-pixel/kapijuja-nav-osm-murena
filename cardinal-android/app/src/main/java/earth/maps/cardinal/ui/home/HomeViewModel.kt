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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import dagger.hilt.android.lifecycle.HiltViewModel
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.ViewportRepository
import earth.maps.cardinal.data.room.RecentSearch
import earth.maps.cardinal.data.room.RecentSearchRepository
import earth.maps.cardinal.data.room.SavedPlaceRepository
import earth.maps.cardinal.geocoding.GeocodingService
import earth.maps.cardinal.ui.core.BaseSearchViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    geocodingService: GeocodingService,
    viewportRepository: ViewportRepository,
    private val savedPlaceRepository: SavedPlaceRepository,
    recentSearchRepository: RecentSearchRepository,
) : BaseSearchViewModel(geocodingService, viewportRepository, recentSearchRepository) {

    // Whether the home screen is in a search state.
    private val _searchExpanded = MutableStateFlow(false)

    val searchExpanded: Flow<Boolean> = _searchExpanded

    // Keep selection state separately from the base viewmodel so that we can preserve it when the
    // user hits the back button to return to the search screen.
    var searchQueryValue by mutableStateOf(
        TextFieldValue()
    )
        private set

    fun updateSearchQuery(query: TextFieldValue) {
        searchQueryValue = query
        updateSearchQuery(query.text)
    }

    fun pinnedPlaces(): Flow<List<Place>> {
        return savedPlaceRepository.getPinnedPlacesAsFlow()
    }

    override suspend fun getPinnedSearchPlaces(): List<Place> {
        return savedPlaceRepository.gePinnedPlacesForSearch()
    }

    fun collapseSearch() {
        _searchExpanded.value = false
    }

    fun expandSearch() {
        _searchExpanded.value = true
    }

    /**
     * Called when a search result is selected/tapped.
     * Adds the place to recent searches.
     */
    override fun onPlaceSelected(place: Place) {
        addRecentSearch(place)
    }

    /**
     * Gets recent searches.
     */
    suspend fun recentSearches(): Flow<List<RecentSearch>> {
        return recentSearchRepository.getRecentSearches()
    }

    fun searchToPlace(search: RecentSearch): Place {
        return recentSearchRepository.toPlace(search)
    }

    suspend fun removeRecentSearch(recentSearch: RecentSearch) {
        recentSearchRepository.removeRecentSearch(recentSearch)
    }
}
