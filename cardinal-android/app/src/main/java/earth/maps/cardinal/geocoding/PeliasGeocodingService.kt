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
import earth.maps.cardinal.BuildConfig
import earth.maps.cardinal.data.Address
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.GeocodeResult
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.PlaceIdGenerator
import earth.maps.cardinal.data.maskSensitiveQueryParamsForLogs
import earth.maps.cardinal.data.toDebugLogString
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "PeliasGeocoding"
class PeliasGeocodingService(
    private val appPreferenceRepository: AppPreferenceRepository,
    locationRepository: LocationRepository,
    private val nearbyCategorySearchCoordinator: NearbyCategorySearchCoordinator
) :
    GeocodingService(locationRepository) {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        if (BuildConfig.DEBUG) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("KtorClient", message.maskSensitiveQueryParamsForLogs())
                    }
                }
                level = LogLevel.INFO
            }
        }
    }

    private val nearbyProviderClient = NearbyProviderClient(
        client = client,
        appPreferenceRepository = appPreferenceRepository,
        parseGeocodeResult = ::parseGeocodeResult,
        logApiCall = ::logApiCall
    )

    override suspend fun geocodeRaw(query: String, focusPoint: LatLng?, autocomplete: Boolean): List<GeocodeResult> {
        try {
            Log.d(TAG, "Geocoding request, autocomplete=$autocomplete, hasFocusPoint=${focusPoint != null}")
            val config = appPreferenceRepository.peliasApiConfig.value
            val endpoint = if (autocomplete) {
                "${config.baseUrl}/autocomplete"
            } else {
                "${config.baseUrl}/search"
            }
            logApiCall(
                operation = if (autocomplete) "autocomplete" else "search",
                endpoint = endpoint,
                config = config.toDebugLogString()
            )
            val response = client.get(endpoint) {
                parameter("text", query)
                parameter("size", "10")
                config.apiKey?.let { parameter("api_key", it) }
                focusPoint?.let {
                    parameter("focus.point.lat", it.latitude.toString())
                    parameter("focus.point.lon", it.longitude.toString())
                }
            }

            val result = response.body<JsonObject>()
            val features = result["features"]?.jsonArray ?: JsonArray(emptyList())
            Log.d(TAG, "Number of features: ${features.size}")

            val geocodeResults = features.mapNotNull { element ->
                parseGeocodeResult(element)
            }
            Log.d(TAG, "Parsed results: ${geocodeResults.size}")

            return geocodeResults
        } catch (e: Exception) {
            Log.e(TAG, "Error during geocoding", e)
            return emptyList()
        }
    }

    override suspend fun reverseGeocodeRaw(
        latitude: Double,
        longitude: Double
    ): List<GeocodeResult> {
        try {
            Log.d(TAG, "Reverse geocoding request")
            val config = appPreferenceRepository.peliasApiConfig.value
            val endpoint = "${config.baseUrl}/reverse"
            logApiCall(
                operation = "reverse",
                endpoint = endpoint,
                config = config.toDebugLogString()
            )
            val response = client.get(endpoint) {
                parameter("point.lat", latitude.toString())
                parameter("point.lon", longitude.toString())
                parameter("size", "10")
                config.apiKey?.let { parameter("api_key", it) }
            }

            val result = response.body<JsonObject>()
            val features = result["features"]?.jsonArray ?: JsonArray(emptyList())
            Log.d(TAG, "Number of reverse features: ${features.size}")

            val geocodeResults = features.mapNotNull { element ->
                parseGeocodeResult(element)
            }
            Log.d(TAG, "Parsed reverse results: ${geocodeResults.size}")

            return geocodeResults
        } catch (e: Exception) {
            return emptyList()
        }
    }

    override suspend fun nearbyRaw(
        latitude: Double,
        longitude: Double,
        selectedCategories: List<String>
    ): List<GeocodeResult> {
        try {
            Log.d(TAG, "Nearby request, selectedCategories=${selectedCategories.size}")
            val geocodeResults = nearbyCategorySearchCoordinator.searchNearbyCategories(
                nearbyProviderClient = nearbyProviderClient,
                latitude = latitude,
                longitude = longitude,
                selectedCategories = selectedCategories
            )
            Log.d(TAG, "Parsed nearby results: ${geocodeResults.size}")

            return geocodeResults
        } catch (e: Exception) {
            Log.e(TAG, "Error during nearby", e)
            return emptyList()
        }
    }

    override suspend fun nearbySearchRaw(
        latitude: Double,
        longitude: Double,
        query: String
    ): List<GeocodeResult> {
        return nearbyProviderClient.searchNearbyText(
            latitude = latitude,
            longitude = longitude,
            query = query
        )
    }

    private fun parseGeocodeResult(element: JsonElement): GeocodeResult? {
        return try {
            val obj = element.jsonObject
            val geometry = obj["geometry"]?.jsonObject
            val coordinates = geometry?.get("coordinates")?.jsonArray

            val lon = coordinates?.getOrNull(0)?.jsonPrimitive?.doubleOrNull
            val lat = coordinates?.getOrNull(1)?.jsonPrimitive?.doubleOrNull

            val properties = obj["properties"]?.jsonObject
            val displayName = properties?.get("label")?.jsonPrimitive?.content ?: ""
            val osmAddendum =
                properties?.get("addendum")?.jsonObject?.get("osm")?.jsonObject?.toMap()
            val tags =
                osmAddendum?.map { (key, value) -> key to value.jsonPrimitive.content }?.toMap()
            val peliasCategories = properties?.get("category")?.jsonArray
                ?.map { category -> category.jsonPrimitive.content }
                .orEmpty()
            val resultProperties = buildMap {
                tags?.let { putAll(it) }
                if (peliasCategories.isNotEmpty()) {
                    put("category", peliasCategories.joinToString(","))
                }
            }

            if (lat != null && lon != null) {
                val address = if (properties != null) {
                    Address(
                        houseNumber = properties["housenumber"]?.jsonPrimitive?.content,
                        road = properties["street"]?.jsonPrimitive?.content,
                        city = properties["locality"]?.jsonPrimitive?.content,
                        state = properties["region"]?.jsonPrimitive?.content,
                        postcode = properties["postalcode"]?.jsonPrimitive?.content,
                        country = properties["country"]?.jsonPrimitive?.content,
                        countryCode = properties["country_code"]?.jsonPrimitive?.content
                    )
                } else {
                    null
                }

                GeocodeResult(
                    geocodeId = properties?.get("gid")?.jsonPrimitive?.contentOrNull ?: PlaceIdGenerator.generateId(
                        latitude = lat,
                        longitude = lon,
                        name = displayName
                    ),
                    latitude = lat,
                    longitude = lon,
                    displayName = displayName,
                    address = address,
                    properties = resultProperties
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun hasSeparateAutocomplete(): Boolean {
        return true
    }

    private fun logApiCall(operation: String, endpoint: String, config: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Calling Pelias $operation endpoint=$endpoint, $config")
        }
    }
}
