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

private const val TAG = "ValhallaRouting"

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
            val url = if (config.apiKey != null) {
                "${config.baseUrl}?api_key=${config.apiKey}"
            } else {
                config.baseUrl
            }
            routeNetworkDiagnostics.logRouteRequest(
                endpoint = config.baseUrl,
                config = { config.toDebugLogString() },
                requestBody = { request }
            )

            val response = client.post {
                url(url)
                contentType(ContentType.Application.Json)
                setBody(request)
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
}
