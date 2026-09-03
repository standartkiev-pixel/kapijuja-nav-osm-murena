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

class NearbyCategorySearchCoordinator @Inject internal constructor(
    private val syntheticCategoryMatcher: SyntheticCategoryMatcher,
    private val fallbackSearchStrategy: SyntheticFallbackSearchStrategy
) {
    internal suspend fun searchNearbyCategories(
        nearbyProviderClient: NearbyProviderClient,
        latitude: Double,
        longitude: Double,
        selectedCategories: List<String>
    ): List<GeocodeResult> {
        val categorySplit = splitNearbyCategoriesForProviderRequests(selectedCategories)
        val selectedSyntheticCategories = categorySplit.syntheticCategories
        val selectedNativeCategories = categorySplit.nativeCategories

        val nearbyNativeResults: List<GeocodeResult>
        val nearbySyntheticResults: List<GeocodeResult>

        when {
            selectedSyntheticCategories.isEmpty() -> {
                nearbyNativeResults = nearbyProviderClient.requestNearbyProviderResults(
                    latitude = latitude,
                    longitude = longitude,
                    selectedCategories = selectedCategories,
                    operation = NearbyProviderOperation.NEARBY
                )
                nearbySyntheticResults = emptyList()
            }

            selectedNativeCategories.isEmpty() -> {
                nearbyNativeResults = emptyList()
                nearbySyntheticResults = filteredSyntheticProviderResults(
                    nearbyProviderClient = nearbyProviderClient,
                    latitude = latitude,
                    longitude = longitude,
                    selectedSyntheticCategories = selectedSyntheticCategories,
                    operation = NearbyProviderOperation.NEARBY
                )
            }

            else -> {
                nearbyNativeResults = nearbyProviderClient.requestNearbyProviderResults(
                    latitude = latitude,
                    longitude = longitude,
                    selectedCategories = selectedNativeCategories,
                    operation = NearbyProviderOperation.NEARBY_NATIVE
                )
                nearbySyntheticResults = filteredSyntheticProviderResults(
                    nearbyProviderClient = nearbyProviderClient,
                    latitude = latitude,
                    longitude = longitude,
                    selectedSyntheticCategories = selectedSyntheticCategories,
                    operation = NearbyProviderOperation.NEARBY_SYNTHETIC
                )
            }
        }

        val nearbyResults = (nearbyNativeResults + nearbySyntheticResults).distinctBy { result ->
            result.geocodeId
        }
        val supplementalResults = fallbackSearchStrategy.searchMissingSyntheticCategories(
            nearbyProviderClient = nearbyProviderClient,
            selectedSyntheticCategories = selectedSyntheticCategories,
            nearbySyntheticResults = nearbySyntheticResults,
            latitude = latitude,
            longitude = longitude
        )
        return (nearbyResults + supplementalResults).distinctBy { result ->
            result.geocodeId
        }
    }

    private suspend fun filteredSyntheticProviderResults(
        nearbyProviderClient: NearbyProviderClient,
        latitude: Double,
        longitude: Double,
        selectedSyntheticCategories: List<String>,
        operation: NearbyProviderOperation
    ): List<GeocodeResult> {
        return syntheticCategoryMatcher.filterBySyntheticCategories(
            results = nearbyProviderClient.requestNearbyProviderResults(
                latitude = latitude,
                longitude = longitude,
                selectedCategories = selectedSyntheticCategories,
                operation = operation
            ),
            selectedCategories = selectedSyntheticCategories
        )
    }
}
