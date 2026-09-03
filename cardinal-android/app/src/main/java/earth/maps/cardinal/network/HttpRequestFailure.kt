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

package earth.maps.cardinal.network

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException

enum class HttpRequestFailure {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    PAYLOAD_TOO_LARGE,
    TOO_MANY_REQUESTS,
    NO_INTERNET,
    REQUEST_TIMEOUT,
    SERVER_ERROR,
    SERVICE_UNAVAILABLE,
    EMPTY_RESPONSE,
    SERIALIZATION,
    UNKNOWN,
}

class HttpRequestException(
    val failure: HttpRequestFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

fun Int.toHttpRequestFailure(): HttpRequestFailure =
    when (this) {
        400, 422 -> HttpRequestFailure.BAD_REQUEST
        401 -> HttpRequestFailure.UNAUTHORIZED
        403 -> HttpRequestFailure.FORBIDDEN
        404 -> HttpRequestFailure.NOT_FOUND
        408 -> HttpRequestFailure.REQUEST_TIMEOUT
        409 -> HttpRequestFailure.CONFLICT
        413 -> HttpRequestFailure.PAYLOAD_TOO_LARGE
        429 -> HttpRequestFailure.TOO_MANY_REQUESTS
        503 -> HttpRequestFailure.SERVICE_UNAVAILABLE
        504 -> HttpRequestFailure.REQUEST_TIMEOUT
        in 500..599 -> HttpRequestFailure.SERVER_ERROR
        else -> HttpRequestFailure.UNKNOWN
    }

fun Throwable.toHttpRequestFailure(): HttpRequestFailure =
    when (this) {
        is CancellationException -> throw this
        is HttpRequestException -> failure
        is HttpRequestTimeoutException,
        is SocketTimeoutException -> HttpRequestFailure.REQUEST_TIMEOUT
        is UnknownHostException,
        is UnresolvedAddressException,
        is ConnectException -> HttpRequestFailure.NO_INTERNET
        is ClientRequestException,
        is ServerResponseException,
        is ResponseException -> response.status.value.toHttpRequestFailure()
        is SerializationException -> HttpRequestFailure.SERIALIZATION
        else -> HttpRequestFailure.UNKNOWN
    }
