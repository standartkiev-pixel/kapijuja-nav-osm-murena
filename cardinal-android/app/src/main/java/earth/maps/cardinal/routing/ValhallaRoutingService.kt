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

package earth.maps.cardinal.routing

import android.util.Log
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.ConnectivityRepository
import earth.maps.cardinal.data.toDebugLogString
import earth.maps.cardinal.network.HttpRequestException
import earth.maps.cardinal.network.HttpRequestFailure
import earth.maps.cardinal.network.toHttpRequestFailure
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "ValhallaRouting"
private const val TRUCK_WAYPOINT_RADIUS_METERS = 100
private const val BUS_WAYPOINT_RADIUS_METERS = 600

class ValhallaRoutingService(
    private val appPreferenceRepository: AppPreferenceRepository,
    private val connectivityRepository: ConnectivityRepository
) : RoutingService {
    private val routeNetworkDiagnostics = ValhallaRouteNetworkDiagnostics()

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
        if (routeNetworkDiagnostics.isEnabled) {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        routeNetworkDiagnostics.logKtorMessage(message)
                    }
                }
                level = LogLevel.INFO
            }
        }
    }

    override suspend fun getRoute(
        request: String,
    ): String {
        try {
            val config = appPreferenceRepository.valhallaApiConfig.value
            val url = if (!config.apiKey.isNullOrBlank()) {
                "${config.baseUrl}?api_key=${config.apiKey}"
            } else {
                config.baseUrl
            }
            val outboundRequest = prepareVehicleRouteRequest(request)
            routeNetworkDiagnostics.logRouteRequest(
                endpoint = config.baseUrl,
                config = { config.toDebugLogString() },
                requestBody = { outboundRequest }
            )

            val response = client.post {
                url(url)
                contentType(ContentType.Application.Json)
                setBody(outboundRequest)
            }
            val responseBody: String = response.body()

            if (!response.status.isSuccess()) {
                // Keep the upstream error body in bugreports. Stadia/Valhalla often explains
                // the exact rejected field here; previously we discarded it and retained only 400.
                Log.e(
                    TAG,
                    "Routing upstream failed status=${response.status.value} body=${responseBody.take(2000)}"
                )
                throw HttpRequestException(response.status.value.toHttpRequestFailure())
            }

            connectivityRepository.reportInternetAvailable()
            return responseBody
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error during routing", e)
            val failure = e.toHttpRequestFailure()
            if (failure == HttpRequestFailure.NO_INTERNET) {
                connectivityRepository.reportInternetUnavailable()
            }
            throw if (e is HttpRequestException) {
                e
            } else {
                HttpRequestException(failure, e)
            }
        }
    }

    /**
     * Valhalla normally correlates a waypoint to the single closest edge when radius is zero.
     * For heavy-vehicle routing this can make a perfectly reachable geographic destination
     * unroutable when the closest edge itself has Truck/Bus access restrictions. A small
     * candidate radius lets Valhalla choose a nearby edge which is legal for the strict
     * heavy-vehicle costing model.
     *
     * This applies to Truck, Bus, their traffic variants, and coach requests that use AUTO
     * access semantics while carrying physical vehicle dimensions in costing_options.auto.
     *
     * This does NOT disable or soften heavy-vehicle restrictions: the costing model still decides
     * which candidate edges are legal. It only broadens waypoint-to-road correlation.
     */
    private fun prepareVehicleRouteRequest(request: String): String {
        return try {
            val root = Json.parseToJsonElement(request).jsonObject
            val costing = root["costing"]?.jsonPrimitive?.content ?: return request
            val waypointRadiusMeters =
                heavyVehicleWaypointRadiusMeters(costing, root) ?: return request

            val locations = root["locations"]?.jsonArray ?: return request
            val updatedLocations = locations.map { locationElement ->
                val location = locationElement.jsonObject
                if (location.containsKey("radius")) {
                    locationElement
                } else {
                    JsonObject(
                        location.toMutableMap().apply {
                            put("radius", JsonPrimitive(waypointRadiusMeters))
                        }
                    )
                }
            }

            JsonObject(
                root.toMutableMap().apply {
                    put("locations", JsonArray(updatedLocations))
                }
            ).toString()
        } catch (_: Exception) {
            // Ferrostar currently emits valid JSON, but routing should never fail only because
            // this compatibility normalization could not parse a future request shape.
            request
        }
    }
}

internal fun isTruckCostingProfile(costing: String): Boolean {
    val profile = ValhallaCostingProfile.fromRouteProviderProfile(costing)
    return profile === ValhallaCostingProfile.Truck ||
        profile is ValhallaCostingProfile.TruckTraffic
}

internal fun isBusCostingProfile(costing: String): Boolean {
    val profile = ValhallaCostingProfile.fromRouteProviderProfile(costing)
    return profile === ValhallaCostingProfile.Bus ||
        profile is ValhallaCostingProfile.BusTraffic
}

internal fun heavyVehicleWaypointRadiusMeters(
    costing: String,
    requestRoot: JsonObject
): Int? {
    if (isTruckCostingProfile(costing)) {
        return TRUCK_WAYPOINT_RADIUS_METERS
    }
    if (isBusCostingProfile(costing)) {
        return BUS_WAYPOINT_RADIUS_METERS
    }

    val profile = ValhallaCostingProfile.fromRouteProviderProfile(costing)
    val isAutoFamily = profile === ValhallaCostingProfile.Auto ||
        profile is ValhallaCostingProfile.AutoTraffic
    if (!isAutoFamily) {
        return null
    }

    val autoOptions = requestRoot["costing_options"]
        ?.jsonObject
        ?.get("auto")
        ?.jsonObject
        ?: return null

    // In Kapijuja, AUTO with explicit heavy-vehicle dimensions and normal access rules is
    // the tourist-coach ("Car" switch) profile. Give it the same 600 m legal-edge search
    // as BUS. The dashed access fallback itself uses ignore_access=true and must NOT get
    // this radius, otherwise Valhalla could snap the fallback destination away from the
    // user's actual requested point.
    val hasHeavyVehicleDimensions =
        listOf("length", "width", "height", "weight").any(autoOptions::containsKey)
    val isAccessFallback = autoOptions["ignore_access"]?.jsonPrimitive?.booleanOrNull == true

    return if (hasHeavyVehicleDimensions && !isAccessFallback) {
        BUS_WAYPOINT_RADIUS_METERS
    } else {
        null
    }
}
