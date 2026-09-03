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

package earth.maps.cardinal.geocoding

import earth.maps.cardinal.data.GeocodeResult
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.PlaceIdGenerator
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

internal class SearchResultPriorityPipeline(
    private val trainStationPrioritizer: TrainStationSearchResultPrioritizer =
        TrainStationSearchResultPrioritizer()
) {
    private val diacriticRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val whitespaceRegex = "\\s+".toRegex()

    fun prioritize(
        query: String,
        providerResults: List<GeocodeResult>,
        savedAndPinnedPlaces: List<Place>,
        surface: SearchSurface = SearchSurface.Home
    ): PrioritizedSearchResults {
        require(surface in SearchSurface.userEnteredTextSurfaces)
        val matchingSavedAndPinnedPlaces = savedAndPinnedPlaces.filter { place ->
            place.matchesQuery(query)
        }
        val remainingProviderResults = trainStationPrioritizer.prioritize(providerResults)
            .filterNot { providerResult ->
                matchingSavedAndPinnedPlaces.any { savedOrPinnedPlace ->
                    savedOrPinnedPlace.isDuplicateOf(providerResult)
                }
            }
        return PrioritizedSearchResults(
            savedAndPinnedPlaces = matchingSavedAndPinnedPlaces,
            providerResults = remainingProviderResults
        )
    }

    private fun Place.matchesQuery(query: String): Boolean {
        val tokens = normalize(query).split(whitespaceRegex).filter { token -> token.isNotBlank() }
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

    private fun Place.isDuplicateOf(other: GeocodeResult): Boolean {
        val placeId = runCatching { PlaceIdGenerator.generateId(this) }.getOrNull()
        val otherPlaceId = other.geocodeId.ifBlank {
            PlaceIdGenerator.generateId(
                latitude = other.latitude,
                longitude = other.longitude,
                name = other.displayName
            )
        }
        if (!placeId.isNullOrBlank() && placeId == otherPlaceId) {
            return true
        }

        if (!hasSameCoordinates(other)) {
            return false
        }

        return normalize(name) == normalize(other.displayName)
    }

    private fun Place.hasSameCoordinates(other: GeocodeResult): Boolean {
        return abs(latLng.latitude - other.latitude) < COORDINATE_EPSILON &&
            abs(latLng.longitude - other.longitude) < COORDINATE_EPSILON
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(diacriticRegex, "")
            .lowercase(Locale.ROOT)
    }

    private companion object {
        private const val COORDINATE_EPSILON = 0.000001
    }
}

internal data class PrioritizedSearchResults(
    val savedAndPinnedPlaces: List<Place>,
    val providerResults: List<GeocodeResult>
) {
    val displayNames: List<String>
        get() = savedAndPinnedPlaces.map { place -> place.name } +
            providerResults.map { result -> result.displayName }
}

internal enum class SearchSurface {
    Home,
    Directions,
    NearbyText;

    companion object {
        val userEnteredTextSurfaces = setOf(Home, Directions, NearbyText)
    }
}
