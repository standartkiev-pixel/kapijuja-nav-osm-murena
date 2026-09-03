/*
 *     Cardinal Maps
 *     Copyright (C) 2026 Cardinal Maps Authors
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

import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.PlaceIdGenerator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

object PinnedPlacesSearchResultPrioritizer {
    private val diacriticRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val whitespaceRegex = "\\s+".toRegex()
    private const val COORDINATE_EPSILON = 0.000001

    fun prioritize(
        query: String,
        geocodePlaces: List<Place>,
        pinnedPlaces: List<Place>
    ): List<Place> {
        val matchingPinnedPlaces = pinnedPlaces.filter { it.matchesQuery(query) }
        if (matchingPinnedPlaces.isEmpty()) {
            return geocodePlaces
        }

        val remainingGeocodePlaces = geocodePlaces.filterNot { geocodePlace ->
            matchingPinnedPlaces.any { pinnedPlace ->
                pinnedPlace.isDuplicateOf(geocodePlace)
            }
        }

        return matchingPinnedPlaces + remainingGeocodePlaces
    }

    private fun Place.matchesQuery(query: String): Boolean {
        val tokens = normalize(query).split(whitespaceRegex).filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return false
        }

        val searchableText = normalize(
            listOfNotNull(
                name,
                description,
                address?.houseNumber,
                address?.road,
                address?.city,
                address?.state,
                address?.postcode,
                address?.country,
                address?.countryCode
            ).joinToString(separator = " ")
        )

        return tokens.all { token -> searchableText.contains(token) }
    }

    private fun Place.isDuplicateOf(other: Place): Boolean {
        val placeId = runCatching { PlaceIdGenerator.generateId(this) }.getOrNull()
        val otherPlaceId = runCatching { PlaceIdGenerator.generateId(other) }.getOrNull()
        if (!placeId.isNullOrBlank() && placeId == otherPlaceId) {
            return true
        }

        if (!hasSameCoordinates(other)) {
            return false
        }

        val normalizedName = normalize(name)
        val otherNormalizedName = normalize(other.name)
        return normalizedName == otherNormalizedName
    }

    private fun Place.hasSameCoordinates(other: Place): Boolean {
        return abs(latLng.latitude - other.latLng.latitude) < COORDINATE_EPSILON &&
            abs(latLng.longitude - other.latLng.longitude) < COORDINATE_EPSILON
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(diacriticRegex, "")
            .lowercase(Locale.ROOT)
    }
}
