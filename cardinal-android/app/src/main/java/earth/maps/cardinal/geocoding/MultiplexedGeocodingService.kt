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
        return if (appPreferenceRepository.offlineMode.value) {
            offlineGeocodingService.geocodeRaw(query, focusPoint, autocomplete)
        } else {
            onlineGeocodingService.geocodeRaw(query, focusPoint, autocomplete)
        }
    }

    override suspend fun reverseGeocodeRaw(
        latitude: Double,
        longitude: Double
    ): List<GeocodeResult> {
        return if (appPreferenceRepository.offlineMode.value) {
            offlineGeocodingService.reverseGeocodeRaw(latitude, longitude)
        } else {
            onlineGeocodingService.reverseGeocodeRaw(latitude, longitude)
        }
    }

    override suspend fun nearbyRaw(
        latitude: Double,
        longitude: Double,
        selectedCategories: List<String>
    ): List<GeocodeResult> {
        return if (appPreferenceRepository.offlineMode.value) {
            offlineGeocodingService.nearbyRaw(latitude, longitude, selectedCategories)
        } else {
            onlineGeocodingService.nearbyRaw(latitude, longitude, selectedCategories)
        }
    }

    override suspend fun nearbySearchRaw(
        latitude: Double,
        longitude: Double,
        query: String
    ): List<GeocodeResult> {
        return if (appPreferenceRepository.offlineMode.value) {
            offlineGeocodingService.nearbySearchRaw(latitude, longitude, query)
        } else {
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
