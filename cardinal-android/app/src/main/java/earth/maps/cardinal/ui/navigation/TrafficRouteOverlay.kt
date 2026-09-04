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
import earth.maps.cardinal.routing.HeavyVehicleAccessApproach
import earth.maps.cardinal.routing.HeavyVehicleAccessRelaxation
import earth.maps.cardinal.routing.TrafficRouteSegments
import earth.maps.cardinal.routing.TrafficSegmentUi
import earth.maps.cardinal.routing.navigationHexColor
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
@MapLibreComposable
fun TrafficRouteMapOverlay(
    context: Context,
    route: Route?,
    remainingSteps: List<RouteStep>?,
    trafficAvailable: Boolean,
    accessApproach: HeavyVehicleAccessApproach? = null,
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

    val accessDashSegments = remember(accessApproach) {
        accessApproach?.route?.geometry
            ?.takeIf { it.size >= 2 }
            ?.toFixedDashSegments(
                dashMeters = accessApproach.relaxation.navigationStyle().dashMeters,
                gapMeters = accessApproach.relaxation.navigationStyle().gapMeters
            )
            .orEmpty()
    }
    val accessStyle = accessApproach?.relaxation?.navigationStyle()
    accessDashSegments.forEach { dash ->
        Polyline(
            points = dash.map { it.toMapLibreLatLng() },
            color = ACCESS_APPROACH_CASING_COLOR,
            lineWidth = ACCESS_APPROACH_CASING_WIDTH,
            zIndex = ACCESS_APPROACH_Z_INDEX
        )
    }
    if (accessStyle != null) {
        accessDashSegments.forEach { dash ->
            Polyline(
                points = dash.map { it.toMapLibreLatLng() },
                color = accessStyle.color,
                lineWidth = ACCESS_APPROACH_LINE_WIDTH,
                zIndex = ACCESS_APPROACH_Z_INDEX + 1
            )
        }
    }

    DestinationFlag(accessApproach?.route ?: route)
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

private data class HeavyVehicleAccessNavigationStyle(
    val color: String,
    val dashMeters: Double,
    val gapMeters: Double
)

private fun HeavyVehicleAccessRelaxation.navigationStyle(): HeavyVehicleAccessNavigationStyle =
    when (this) {
        HeavyVehicleAccessRelaxation.ACCESS_ONLY ->
            HeavyVehicleAccessNavigationStyle("#FFB700", 7.0, 5.0)

        HeavyVehicleAccessRelaxation.WEIGHT_RELAXED ->
            HeavyVehicleAccessNavigationStyle("#FF7A00", 10.0, 4.0)

        HeavyVehicleAccessRelaxation.WEIGHT_AND_LENGTH_RELAXED ->
            HeavyVehicleAccessNavigationStyle("#E53935", 4.0, 4.0)
    }

private fun List<GeographicCoordinate>.toFixedDashSegments(
    dashMeters: Double = 7.0,
    gapMeters: Double = 5.0
): List<List<GeographicCoordinate>> {
    if (size < 2) return emptyList()

    val result = mutableListOf<List<GeographicCoordinate>>()
    var phaseMeters = 0.0

    zipWithNext().forEach { (start, end) ->
        val edgeMeters = start.distanceMetersTo(end)
        if (edgeMeters <= 0.1) return@forEach

        val pieceCount = ceil(edgeMeters / 2.0).toInt().coerceAtLeast(1)
        for (piece in 0 until pieceCount) {
            val t0 = piece.toDouble() / pieceCount
            val t1 = (piece + 1).toDouble() / pieceCount
            val midpointMeters = phaseMeters + edgeMeters * ((t0 + t1) / 2.0)
            val period = dashMeters + gapMeters
            if ((midpointMeters % period) < dashMeters) {
                result += listOf(
                    start.interpolateTo(end, t0),
                    start.interpolateTo(end, t1)
                )
            }
        }
        phaseMeters += edgeMeters
    }
    return result
}

private fun GeographicCoordinate.interpolateTo(
    other: GeographicCoordinate,
    fraction: Double
): GeographicCoordinate = GeographicCoordinate(
    lat = lat + (other.lat - lat) * fraction,
    lng = lng + (other.lng - lng) * fraction
)

private fun GeographicCoordinate.distanceMetersTo(other: GeographicCoordinate): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(lat)
    val lat2 = Math.toRadians(other.lat)
    val deltaLat = lat2 - lat1
    val deltaLng = Math.toRadians(other.lng - lng)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(lat1) * cos(lat2) * sin(deltaLng / 2) * sin(deltaLng / 2)
    return 2 * earthRadiusMeters * atan2(sqrt(a), sqrt(1 - a))
}

private const val TRAFFIC_LINE_WIDTH = 8f
private const val TRAFFIC_Z_INDEX = 1
private const val ACCESS_APPROACH_Z_INDEX = 3
private const val ACCESS_APPROACH_CASING_WIDTH = 10f
private const val ACCESS_APPROACH_LINE_WIDTH = 6f
private const val ACCESS_APPROACH_CASING_COLOR = "#282828"
private const val DESTINATION_FLAG_Z_INDEX = 5
