/*
 *     Copyright (C) 2026 e Foundation
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
package earth.maps.cardinal.ui.directions

import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import com.stadiamaps.ferrostar.core.NoResponseBodyException
import earth.maps.cardinal.network.HttpRequestException
import earth.maps.cardinal.network.HttpRequestFailure
import earth.maps.cardinal.network.toHttpRequestFailure
import uniffi.ferrostar.ParsingException

sealed class DirectionUiError {
    object DistanceExceeded : DirectionUiError()
    object InvalidRouteRequest : DirectionUiError()
    object RouteNotFound : DirectionUiError()
    object ConnectionUnavailable : DirectionUiError()
    object RequestTimedOut : DirectionUiError()
    object TooManyRequests : DirectionUiError()
    object RoutingServiceSettings : DirectionUiError()
    object ServerUnavailable : DirectionUiError()
    object RouteParsingFailed : DirectionUiError()
    object Unknown : DirectionUiError()
}

enum class DirectionCodes(val value: String) {
    DISTANCE_EXCEEDED("DistanceExceeded")
}

private val routeNotFoundCodes = listOf(
    "NoRoute",
    "NoRouteFound",
    "NoPath",
    "NoPathFound",
    "NoSegment",
    "NoSuitableEdgesNearLocation",
    "NoSuitableEdgesNearDestination"
)

fun ParsingException.InvalidStatusCode.toRouteError(): DirectionUiError =
    when (this.code) {
        DirectionCodes.DISTANCE_EXCEEDED.value -> DirectionUiError.DistanceExceeded
        in routeNotFoundCodes -> DirectionUiError.RouteNotFound
        else -> DirectionUiError.RouteParsingFailed
    }

fun Throwable.toRouteError(): DirectionUiError =
    when (this) {
        is ParsingException.InvalidStatusCode -> toRouteError()
        is InvalidStatusCodeException -> statusCode.toRoutingError()
        is NoResponseBodyException -> DirectionUiError.RouteParsingFailed
        is HttpRequestException -> failure.toRoutingError()
        is ParsingException -> DirectionUiError.RouteParsingFailed
        else -> toHttpRequestFailure().toRoutingError()
    }

private fun Int.toRoutingError(): DirectionUiError =
    toHttpRequestFailure().toRoutingError()

private fun HttpRequestFailure.toRoutingError(): DirectionUiError =
    when (this) {
        HttpRequestFailure.BAD_REQUEST,
        HttpRequestFailure.CONFLICT,
        HttpRequestFailure.PAYLOAD_TOO_LARGE -> DirectionUiError.InvalidRouteRequest
        HttpRequestFailure.UNAUTHORIZED,
        HttpRequestFailure.FORBIDDEN -> DirectionUiError.RoutingServiceSettings
        HttpRequestFailure.NOT_FOUND -> DirectionUiError.RouteNotFound
        HttpRequestFailure.NO_INTERNET,
        HttpRequestFailure.SERVICE_UNAVAILABLE -> DirectionUiError.ConnectionUnavailable
        HttpRequestFailure.REQUEST_TIMEOUT -> DirectionUiError.RequestTimedOut
        HttpRequestFailure.TOO_MANY_REQUESTS -> DirectionUiError.TooManyRequests
        HttpRequestFailure.SERVER_ERROR -> DirectionUiError.ServerUnavailable
        HttpRequestFailure.EMPTY_RESPONSE,
        HttpRequestFailure.SERIALIZATION -> DirectionUiError.RouteParsingFailed
        HttpRequestFailure.UNKNOWN -> DirectionUiError.Unknown
    }
