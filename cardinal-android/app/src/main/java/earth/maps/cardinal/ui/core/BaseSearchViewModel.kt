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

package earth.maps.cardinal.ui.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.ViewportRepository
import earth.maps.cardinal.data.room.RecentSearchRepository
import earth.maps.cardinal.geocoding.GeocodingService
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Base ViewModel that provides common search functionality
 * to reduce duplication across ViewModels that need search capabilities
 */
@OptIn(FlowPreview::class)
abstract class BaseSearchViewModel(
    protected val geocodingService: GeocodingService,
    protected val viewportRepository: ViewportRepository,
    protected val recentSearchRepository: RecentSearchRepository
) : ViewModel() {

    // Search query flow for debouncing
    private val _searchQueryFlow = MutableStateFlow("")
    protected val searchQueryFlow: StateFlow<String> = _searchQueryFlow.asStateFlow()

    var searchQuery by mutableStateOf("")
        protected set

    val geocodeResults = mutableStateOf<List<Place>>(emptyList())

    var isSearching by mutableStateOf(false)
        protected set

    var searchError by mutableStateOf<String?>(null)
        protected set

    val expandedResultsAvailable: Boolean get() = geocodingService.hasSeparateAutocomplete()

    init {
        // Set up debounced search
        searchQueryFlow
            .debounce(300) // 300ms delay
            .distinctUntilChanged()
            .onEach { query ->
                if (query.isNotEmpty()) {
                    performSearch(query)
                } else {
                    // Clear results when query is empty
                    geocodeResults.value = emptyList()
                    searchError = null
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Updates the search query and triggers debounced search
     */
    fun updateSearchQuery(query: String) {
        searchQuery = query
        _searchQueryFlow.value = query
    }

    /**
     * Performs the actual search operation
     * Can be overridden by subclasses to provide custom focus point logic
     */
    protected open fun performSearch(query: String, autoComplete: Boolean = true) {
        viewModelScope.launch {
            isSearching = true
            searchError = null
            val savedPlaces = getSavedSearchPlacesSafely()
            try {
                // Get focus point for viewport biasing - subclasses can override this
                val focusPoint = getSearchFocusPoint()
                val geocodedPlaces = geocodingService.geocode(query, focusPoint, autoComplete)
                geocodeResults.value = PinnedPlacesSearchResultPrioritizer.prioritize(
                    query = query,
                    geocodePlaces = geocodedPlaces,
                    pinnedPlaces = savedPlaces
                )
                isSearching = false
            } catch (e: Exception) {
                // Handle error
                searchError = e.message ?: "An error occurred during search"
                geocodeResults.value = PinnedPlacesSearchResultPrioritizer.prioritize(
                    query = query,
                    geocodePlaces = emptyList(),
                    pinnedPlaces = savedPlaces
                )
                isSearching = false
            }
        }
    }

    protected open suspend fun getPinnedSearchPlaces(): List<Place> {
        return emptyList()
    }

    private suspend fun getSavedSearchPlacesSafely(): List<Place> {
        return runCatching { getPinnedSearchPlaces() }.getOrDefault(emptyList())
    }

    /**
     * Determines the focus point for search viewport biasing
     * Subclasses can override this to provide custom logic
     */
    protected open suspend fun getSearchFocusPoint(): LatLng? {
        return viewportRepository.viewportCenter.value
    }

    /**
     * Adds a place to recent searches
     */
    protected fun addRecentSearch(place: Place) {
        viewModelScope.launch {
            recentSearchRepository.addRecentSearch(place)
        }
    }

    /**
     * Called when a search result is selected
     * Subclasses can override this to provide custom behavior
     */
    open fun onPlaceSelected(place: Place) {
        addRecentSearch(place)
    }

    fun rerunWithoutAutocomplete() {
        performSearch(searchQuery, autoComplete = false)
    }
}
