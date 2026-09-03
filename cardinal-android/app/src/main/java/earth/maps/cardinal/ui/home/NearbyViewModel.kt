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

import android.location.Location
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.geocoding.GeocodingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NearbyViewModel @Inject constructor(
    private val geocodingService: GeocodingService,
    private val locationRepository: LocationRepository,
    private val nearbyFilterPolicy: NearbyFilterPolicy
) : ViewModel() {

    private companion object {
        private const val TAG = "NearbyViewModel"
    }

    private val _nearbyResults = MutableStateFlow<List<Place>>(emptyList())
    val nearbyResults: StateFlow<List<Place>> = _nearbyResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var lastLocation: Location? = null

    private val _selectedCategories = MutableStateFlow<Set<String>>(emptySet())
    val selectedCategories = _selectedCategories.asStateFlow()

    private val _categorySearchQuery = MutableStateFlow("")
    val categorySearchQuery = _categorySearchQuery.asStateFlow()

    private val _draftSelectedCategories = MutableStateFlow<Set<String>>(emptySet())
    val draftSelectedCategories = _draftSelectedCategories.asStateFlow()

    private val _draftCategorySearchQuery = MutableStateFlow("")
    val draftCategorySearchQuery = _draftCategorySearchQuery.asStateFlow()

    private val _isFilterApplied = MutableStateFlow(false)
    val isFilterApplied = _isFilterApplied.asStateFlow()

    init {
        // Start observing location updates
        startLocationObservation()
    }

    val allCategories = nearbyFilterPolicy.allCategories

    val categoryFilters = nearbyFilterPolicy.categoryFilters

    fun toggleCategorySelection(category: String) {
        if (_selectedCategories.value.contains(category)) {
            _selectedCategories.value = _selectedCategories.value.minus(category)
        } else {
            _selectedCategories.value = _selectedCategories.value.plus(category)
        }
        _categorySearchQuery.value = ""
        syncDraftFilters()
        updateFilterApplied()
        refreshData()
    }

    fun beginCategoryFilterEdit() {
        syncDraftFilters()
    }

    fun updateDraftCategorySearchQuery(query: String) {
        _draftCategorySearchQuery.value = query
    }

    fun toggleDraftCategorySelection(category: String) {
        if (_draftSelectedCategories.value.contains(category)) {
            _draftSelectedCategories.value = _draftSelectedCategories.value.minus(category)
        } else {
            _draftSelectedCategories.value = _draftSelectedCategories.value.plus(category)
        }
    }

    fun applyCategoryFilters() {
        val appliedFilterState = nearbyFilterPolicy.appliedFilterState(
            draftSelectedCategories = _draftSelectedCategories.value,
            draftCategorySearchQuery = _draftCategorySearchQuery.value
        )
        _selectedCategories.value = appliedFilterState.selectedCategories
        _categorySearchQuery.value = appliedFilterState.searchQuery
        _isFilterApplied.value = appliedFilterState.isFilterApplied
        refreshData()
    }

    fun resetDraftCategoryFilters() {
        _draftSelectedCategories.value = emptySet()
        _draftCategorySearchQuery.value = ""
    }

    /**
     * Starts observing location updates from the LocationRepository.
     * This method is made open for testing purposes.
     */
    @VisibleForTesting
    fun startLocationObservation() {
        viewModelScope.launch {
            locationRepository.locationFlow.distinctUntilChanged(areEquivalent = { pos1, pos2 ->
                // Change if either is null (*new* null values are filtered by the null check in collectLatest
                if (pos1 == null || pos2 == null) {
                    return@distinctUntilChanged false
                }
                // Only update if location changed significantly (more than 250 meters)
                pos2.distanceTo(pos1) < 250f // true means equivalency
            }).collectLatest { location ->
                location?.let {
                    lastLocation = it
                    Log.d(TAG, "Location updated: $lastLocation")
                    fetchNearby(location.latitude, location.longitude)
                }
            }
        }
    }

    @VisibleForTesting
    fun getSelectedCategoriesSet(): Set<String> {
        return selectedCategories.value.toSet()
    }

    @VisibleForTesting
    fun getEffectiveCategoriesList(): List<String> {
        return effectiveCategories()
    }

    suspend fun fetchNearby(latitude: Double, longitude: Double) {
        _isLoading.value = true
        _error.value = null
        try {
            _nearbyResults.value = nearbyResultsForAppliedFilters(latitude, longitude)
            _isLoading.value = false
        } catch (e: Exception) {
            _error.value = e.message ?: "Error fetching nearby places"
            _nearbyResults.value = emptyList()
            _isLoading.value = false
        }
    }

    /**
     * Manually refreshes the nearby data using current location.
     */
    fun refreshData() {
        lastLocation?.let { location ->
            viewModelScope.launch {
                fetchNearby(location.latitude, location.longitude)
            }
        }
    }

    private fun syncDraftFilters() {
        _draftSelectedCategories.value = _selectedCategories.value
        _draftCategorySearchQuery.value = _categorySearchQuery.value
    }

    private fun updateFilterApplied() {
        _isFilterApplied.value = nearbyFilterPolicy.isFilterApplied(
            selectedCategories = _selectedCategories.value,
            searchQuery = _categorySearchQuery.value
        )
    }

    private suspend fun nearbyResultsForAppliedFilters(latitude: Double, longitude: Double): List<Place> {
        return when (
            val query = nearbyFilterPolicy.queryForAppliedFilters(
                selectedCategories = _selectedCategories.value,
                searchQuery = _categorySearchQuery.value
            )
        ) {
            is NearbyFilterQuery.Categories -> geocodingService.nearby(latitude, longitude, query.categories)
            is NearbyFilterQuery.TextSearch -> geocodingService.nearbySearch(latitude, longitude, query.query)
        }
    }

    private fun effectiveCategories(): List<String> {
        return nearbyFilterPolicy.effectiveCategories(_selectedCategories.value)
    }
}
