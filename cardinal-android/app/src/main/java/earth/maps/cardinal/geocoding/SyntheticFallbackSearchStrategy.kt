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

package earth.maps.cardinal.geocoding

import earth.maps.cardinal.data.GeocodeResult
import javax.inject.Inject

internal class SyntheticFallbackSearchStrategy @Inject constructor(
    private val syntheticCategoryMatcher: SyntheticCategoryMatcher
) {
    suspend fun searchMissingSyntheticCategories(
        nearbyProviderClient: NearbyProviderClient,
        selectedSyntheticCategories: List<String>,
        nearbySyntheticResults: List<GeocodeResult>,
        latitude: Double,
        longitude: Double
    ): List<GeocodeResult> {
        return selectedSyntheticCategories.flatMap { selectedCategory ->
            val existingNearbyCount = nearbySyntheticResults.count { result ->
                syntheticCategoryMatcher.matches(result, selectedCategory)
            }
            if (existingNearbyCount >= NEARBY_SYNTHETIC_FALLBACK_MIN_RESULTS) {
                emptyList()
            } else {
                searchSyntheticCategory(nearbyProviderClient, selectedCategory, latitude, longitude)
            }
        }.distinctBy { result -> result.geocodeId }
    }

    private suspend fun searchSyntheticCategory(
        nearbyProviderClient: NearbyProviderClient,
        selectedCategory: String,
        latitude: Double,
        longitude: Double
    ): List<GeocodeResult> {
        val fallbackSearch = syntheticFallbackSearchFor(selectedCategory) ?: return emptyList()
        val fallbackResults = nearbyProviderClient.searchSyntheticFallback(
            fallbackSearch = fallbackSearch,
            latitude = latitude,
            longitude = longitude
        )
        return syntheticCategoryMatcher.filterBySyntheticCategories(
            results = fallbackResults,
            selectedCategories = listOf(selectedCategory)
        )
    }
}
