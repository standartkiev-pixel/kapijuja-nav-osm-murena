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
import earth.maps.cardinal.data.GeocodeResult.Companion.generatePlaceId
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.Place

abstract class GeocodingService(private val locationRepository: LocationRepository) {

    /**
     * Geocode a query string to find matching locations, returning Place objects.
     * @param query The search query (e.g., address, place name)
     * @param focusPoint Optional focus point for viewport biasing
     * @return Place objects
     */
    suspend fun geocode(query: String, focusPoint: LatLng? = null, autocomplete: Boolean = true): List<Place> {
        return convertResultsToPlaces(geocodeRaw(query, focusPoint, autocomplete))
    }

    /**
     * Reverse geocode coordinates to find address information, returning Place objects.
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     * @return Place objects
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): List<Place> {
        return convertResultsToPlaces(reverseGeocodeRaw(latitude, longitude))
    }

    /**
     * Query whether the is service has separate autocomplete behavior.
     * @return true if this geocoding service has separate behavior for autocomplete and "normal"
     * full-text search. false otherwise.
     */
    abstract fun hasSeparateAutocomplete(): Boolean

    /**
     * Find nearby places around a given point, returning Place objects.
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     * @return Place objects
     */
    suspend fun nearby(latitude: Double, longitude: Double, selectedCategories: List<String>): List<Place> {
        return convertResultsToPlaces(nearbyRaw(latitude, longitude, selectedCategories))
    }

    suspend fun nearbySearch(latitude: Double, longitude: Double, query: String): List<Place> {
        return convertResultsToPlaces(nearbySearchRaw(latitude, longitude, query))
    }

    /**
     * Converts a list of GeocodeResult to a list of Place, including deduplication.
     * @param results The GeocodeResults to convert.
     * @return Place objects.
     */
    protected open suspend fun convertResultsToPlaces(results: List<GeocodeResult>): List<Place> {
        val deduplicatedResults = deduplicateSearchResults(results)
        return deduplicatedResults.map { geocodeResult ->
            locationRepository.createSearchResultPlace(geocodeResult)
        }
    }

    /**
     * Geocode a query string to find matching locations, returning raw GeocodeResult objects.
     * @param query The search query (e.g., address, place name)
     * @param focusPoint Optional focus point for viewport biasing
     * @return Raw geocoding results
     */
    abstract suspend fun geocodeRaw(
        query: String,
        focusPoint: LatLng? = null,
        autocomplete: Boolean
    ): List<GeocodeResult>

    /**
     * Reverse geocode coordinates to find address information, returning raw GeocodeResult objects.
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     * @return Raw geocoding results
     */
    abstract suspend fun reverseGeocodeRaw(
        latitude: Double,
        longitude: Double
    ): List<GeocodeResult>

    /**
     * Find nearby places around a given point, returning raw GeocodeResult objects.
     * @param latitude The latitude coordinate
     * @param longitude The longitude coordinate
     * @return Raw nearby places
     */
    abstract suspend fun nearbyRaw(
        latitude: Double,
        longitude: Double,
        selectedCategories: List<String>
    ): List<GeocodeResult>

    open suspend fun nearbySearchRaw(
        latitude: Double,
        longitude: Double,
        query: String
    ): List<GeocodeResult> {
        return geocodeRaw(
            query = query,
            focusPoint = LatLng(latitude, longitude),
            autocomplete = false
        )
    }
}

fun deduplicateSearchResults(results: List<GeocodeResult>): List<GeocodeResult> {
    return results.distinctBy { generatePlaceId(it) }
}
