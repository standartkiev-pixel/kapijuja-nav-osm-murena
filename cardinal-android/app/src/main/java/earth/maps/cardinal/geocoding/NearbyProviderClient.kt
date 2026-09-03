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

import android.util.Log
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.GeocodeResult
import earth.maps.cardinal.data.toDebugLogString
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray

private const val TAG = "NearbyProviderClient"

internal enum class NearbyProviderOperation(val logLabel: String) {
    NEARBY("nearby"),
    NEARBY_NATIVE("nearby native"),
    NEARBY_SYNTHETIC("nearby synthetic"),
    NEARBY_SEARCH("nearby search");

    companion object {
        fun syntheticFallbackLogLabel(syntheticFilter: String): String {
            return "nearby $syntheticFilter fallback"
        }
    }
}

internal class NearbyProviderClient(
    private val client: HttpClient,
    private val appPreferenceRepository: AppPreferenceRepository,
    private val parseGeocodeResult: (JsonElement) -> GeocodeResult?,
    private val logApiCall: (operation: String, endpoint: String, config: String) -> Unit
) {
    suspend fun searchNearbyText(
        latitude: Double,
        longitude: Double,
        query: String
    ): List<GeocodeResult> {
        val searchText = query.trim()
        if (searchText.isEmpty()) {
            return emptyList()
        }

        return requestNearbySearch(
            latitude = latitude,
            longitude = longitude,
            text = searchText,
            resultSize = NEARBY_MAX_SIZE,
            operation = NearbyProviderOperation.NEARBY_SEARCH.logLabel
        )
    }

    suspend fun requestNearbyProviderResults(
        latitude: Double,
        longitude: Double,
        selectedCategories: List<String>,
        operation: NearbyProviderOperation
    ): List<GeocodeResult> {
        val config = appPreferenceRepository.nearbyApiConfig.value
        val endpoint = "${config.baseUrl}/nearby"
        logApiCall(operation.logLabel, endpoint, config.toDebugLogString())
        val providerCategories = selectedCategories.toProviderNearbyCategories()
        val response = client.get(endpoint) {
            parameter("point.lat", latitude.toString())
            parameter("point.lon", longitude.toString())
            parameter("size", NEARBY_MAX_SIZE.toString())
            parameter("layers", "venue")
            if (providerCategories.isNotEmpty()) {
                parameter("categories", providerCategories.joinToString(","))
            }
            config.apiKey?.let { parameter("api_key", it) }
        }

        val result = response.body<JsonObject>()
        val features = result["features"]?.jsonArray ?: JsonArray(emptyList())
        Log.d(TAG, "Number of nearby features (${operation.logLabel}): ${features.size}")

        return features.mapNotNull { element ->
            parseGeocodeResult(element)
        }
    }

    suspend fun searchSyntheticFallback(
        fallbackSearch: SyntheticFallbackSearch,
        latitude: Double,
        longitude: Double
    ): List<GeocodeResult> {
        val results = mutableListOf<GeocodeResult>()

        for (term in fallbackSearch.searchTerms) {
            try {
                results += requestNearbySearch(
                    latitude = latitude,
                    longitude = longitude,
                    text = term,
                    resultSize = fallbackSearch.resultSize,
                    operation = NearbyProviderOperation.syntheticFallbackLogLabel(fallbackSearch.syntheticFilter)
                )
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error during ${NearbyProviderOperation.syntheticFallbackLogLabel(fallbackSearch.syntheticFilter)}",
                    e
                )
            }
        }

        return results.distinctBy { result -> result.geocodeId }
    }

    private suspend fun requestNearbySearch(
        latitude: Double,
        longitude: Double,
        text: String,
        resultSize: Int,
        operation: String
    ): List<GeocodeResult> {
        val config = appPreferenceRepository.nearbyApiConfig.value
        val endpoint = "${config.baseUrl}/search"
        logApiCall(operation, endpoint, config.toDebugLogString())
        val response = client.get(endpoint) {
            parameter("text", text)
            parameter("size", resultSize.toString())
            parameter("layers", "venue")
            parameter("focus.point.lat", latitude.toString())
            parameter("focus.point.lon", longitude.toString())
            parameter("boundary.circle.lat", latitude.toString())
            parameter("boundary.circle.lon", longitude.toString())
            parameter("boundary.circle.radius", NEARBY_SUPPLEMENTAL_RADIUS_KM)
            config.apiKey?.let { parameter("api_key", it) }
        }

        val result = response.body<JsonObject>()
        val features = result["features"]?.jsonArray ?: JsonArray(emptyList())
        Log.d(TAG, "Number of $operation features: ${features.size}")

        return features.mapNotNull { element ->
            parseGeocodeResult(element)
        }
    }
}
