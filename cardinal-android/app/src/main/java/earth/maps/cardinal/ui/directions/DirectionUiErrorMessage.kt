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

package earth.maps.cardinal.ui.directions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import earth.maps.cardinal.R.string

@Composable
internal fun directionUiErrorMessage(error: DirectionUiError): String =
    when (error) {
        DirectionUiError.DistanceExceeded -> stringResource(string.long_itinerary_error_message)
        DirectionUiError.InvalidRouteRequest -> stringResource(string.routing_error_invalid_route_request)
        DirectionUiError.RouteNotFound -> stringResource(string.routing_error_route_not_found)
        DirectionUiError.ConnectionUnavailable -> stringResource(string.routing_error_connection_unavailable)
        DirectionUiError.RequestTimedOut -> stringResource(string.routing_error_request_timed_out)
        DirectionUiError.TooManyRequests -> stringResource(string.routing_error_too_many_requests)
        DirectionUiError.RoutingServiceSettings -> stringResource(string.routing_error_service_settings)
        DirectionUiError.ServerUnavailable -> stringResource(string.routing_error_server_unavailable)
        DirectionUiError.RouteParsingFailed -> stringResource(string.routing_error_route_parsing_failed)
        DirectionUiError.Unknown -> stringResource(string.routing_error_unknown)
    }
