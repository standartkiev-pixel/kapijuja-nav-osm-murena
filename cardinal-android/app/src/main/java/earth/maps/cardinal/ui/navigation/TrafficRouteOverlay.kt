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

package earth.maps.cardinal.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.maplibre.compose.ramani.MapLibreComposable
import com.maplibre.compose.symbols.Polyline
import com.maplibre.compose.symbols.Symbol
import earth.maps.cardinal.R
import earth.maps.cardinal.routing.TrafficRouteSegments
import earth.maps.cardinal.routing.TrafficSegmentUi
import earth.maps.cardinal.routing.navigationHexColor
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep

@Composable
@MapLibreComposable
fun TrafficRouteMapOverlay(
    context: Context,
    route: Route?,
    remainingSteps: List<RouteStep>?,
    trafficAvailable: Boolean,
) {
    val trafficSegments = remember(route, remainingSteps, trafficAvailable) {
        val currentSteps = remainingSteps.orEmpty()
        when {
            currentSteps.isNotEmpty() &&
                (trafficAvailable || TrafficRouteSegments.trafficAvailable(currentSteps)) ->
                TrafficRouteSegments.build(currentSteps)

            route != null && (trafficAvailable || TrafficRouteSegments.trafficAvailable(route)) ->
                TrafficRouteSegments.build(route)

            else -> emptyList()
        }.mergeAdjacentSegments()
    }

    trafficSegments.forEach { segment ->
        Polyline(
            points = segment.coordinates.map { it.toMapLibreLatLng() },
            color = segment.level.navigationHexColor(context),
            lineWidth = TRAFFIC_LINE_WIDTH,
            zIndex = TRAFFIC_Z_INDEX
        )
    }

    DestinationFlag(route)
}

@Composable
@MapLibreComposable
private fun DestinationFlag(route: Route?) {
    val destination = route?.geometry?.lastOrNull() ?: return

    Symbol(
        center = destination.toMapLibreLatLng(),
        imageId = R.drawable.ic_destination_flag,
        imageAnchor = ICON_ANCHOR_BOTTOM,
        zIndex = DESTINATION_FLAG_Z_INDEX
    )
}

private fun List<TrafficSegmentUi>.mergeAdjacentSegments(): List<TrafficSegmentUi> {
    if (isEmpty()) {
        return emptyList()
    }

    val merged = mutableListOf<TrafficSegmentUi>()
    forEach { segment ->
        val previous = merged.lastOrNull()
        if (
            previous != null &&
            previous.level == segment.level &&
            previous.coordinates.lastOrNull() == segment.coordinates.firstOrNull()
        ) {
            merged[merged.lastIndex] = previous.copy(
                coordinates = previous.coordinates + segment.coordinates.drop(1)
            )
        } else {
            merged += segment
        }
    }
    return merged
}

private fun GeographicCoordinate.toMapLibreLatLng(): LatLng = LatLng(lat, lng)

private const val TRAFFIC_LINE_WIDTH = 8f
private const val TRAFFIC_Z_INDEX = 1
private const val DESTINATION_FLAG_Z_INDEX = 2
