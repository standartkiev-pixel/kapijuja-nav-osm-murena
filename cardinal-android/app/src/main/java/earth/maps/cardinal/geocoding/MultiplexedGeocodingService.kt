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

import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.GeocodeResult
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository

class MultiplexedGeocodingService(
    private val appPreferenceRepository: AppPreferenceRepository,
    private val onlineGeocodingService: GeocodingService,
    private val offlineGeocodingService: GeocodingService,
    locationRepository: LocationRepository,
) : GeocodingService(locationRepository) {

    override suspend fun geocodeRaw(query: String, focusPoint: LatLng?, autocomplete: Boolean): List<GeocodeResult> {
        if (!appPreferenceRepository.offlineMode.value) {
            return onlineGeocodingService.geocodeRaw(query, focusPoint, autocomplete)
        }

        val offlineResults = offlineGeocodingService.geocodeRaw(query, focusPoint, autocomplete)
        return offlineResults.ifEmpty {
            onlineGeocodingService.geocodeRaw(query, focusPoint, autocomplete)
        }
    }

    override suspend fun reverseGeocodeRaw(
        latitude: Double,
        longitude: Double
    ): List<GeocodeResult> {
        if (!appPreferenceRepository.offlineMode.value) {
            return onlineGeocodingService.reverseGeocodeRaw(latitude, longitude)
        }

        val offlineResults = offlineGeocodingService.reverseGeocodeRaw(latitude, longitude)
        return offlineResults.ifEmpty {
            onlineGeocodingService.reverseGeocodeRaw(latitude, longitude)
        }
    }

    override suspend fun nearbyRaw(
        latitude: Double,
        longitude: Double,
        selectedCategories: List<String>
    ): List<GeocodeResult> {
        if (!appPreferenceRepository.offlineMode.value) {
            return onlineGeocodingService.nearbyRaw(latitude, longitude, selectedCategories)
        }

        val offlineResults =
            offlineGeocodingService.nearbyRaw(latitude, longitude, selectedCategories)
        return offlineResults.ifEmpty {
            onlineGeocodingService.nearbyRaw(latitude, longitude, selectedCategories)
        }
    }

    override suspend fun nearbySearchRaw(
        latitude: Double,
        longitude: Double,
        query: String
    ): List<GeocodeResult> {
        if (!appPreferenceRepository.offlineMode.value) {
            return onlineGeocodingService.nearbySearchRaw(latitude, longitude, query)
        }

        val offlineResults =
            offlineGeocodingService.nearbySearchRaw(latitude, longitude, query)
        return offlineResults.ifEmpty {
            onlineGeocodingService.nearbySearchRaw(latitude, longitude, query)
        }
    }

    override fun hasSeparateAutocomplete(): Boolean {
        return if (appPreferenceRepository.offlineMode.value) {
            offlineGeocodingService.hasSeparateAutocomplete()
        } else {
            onlineGeocodingService.hasSeparateAutocomplete()
        }
    }
}
