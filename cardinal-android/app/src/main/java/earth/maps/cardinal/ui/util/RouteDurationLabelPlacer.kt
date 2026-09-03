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

package earth.maps.cardinal.ui.util

import earth.maps.cardinal.data.LatLng
import uniffi.ferrostar.Route

data class RouteDurationLabel(
    val routeIndex: Int,
    val route: Route,
    val position: LatLng
)

data class RouteLabelBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double
) {
    fun contains(position: LatLng): Boolean {
        val isWithinLatitude = position.latitude in south..north
        val isWithinLongitude = if (west <= east) {
            position.longitude in west..east
        } else {
            position.longitude >= west || position.longitude <= east
        }
        return isWithinLatitude && isWithinLongitude
    }
}

class RouteDurationLabelPlacer(
    private val minLabelDistanceMeters: Double = MIN_ROUTE_LABEL_DISTANCE_METERS
) {
    fun place(
        routes: List<Route>,
        preferredPositions: Map<Route, LatLng>,
        visibleBounds: RouteLabelBounds? = null
    ): List<RouteDurationLabel> {
        val placedPositions = mutableListOf<LatLng>()
        val displayedRoutes = routes.reversed()

        return displayedRoutes.mapIndexedNotNull { index, route ->
            val preferredPosition = preferredPositions[route]
            val position = if (
                preferredPosition != null &&
                preferredPosition.isVisibleIn(visibleBounds) &&
                !preferredPosition.overlapsAny(placedPositions)
            ) {
                preferredPosition
            } else {
                nonOverlappingRoutePoint(
                    route = route,
                    routeIndex = index,
                    routeCount = displayedRoutes.size,
                    placedPositions = placedPositions,
                    visibleBounds = visibleBounds
                )
            }

            position?.also(placedPositions::add)?.let {
                RouteDurationLabel(routeIndex = index, route = route, position = it)
            }
        }
    }

    private fun nonOverlappingRoutePoint(
        route: Route,
        routeIndex: Int,
        routeCount: Int,
        placedPositions: List<LatLng>,
        visibleBounds: RouteLabelBounds?
    ): LatLng? {
        val candidates = routeLabelCandidateFractions(routeIndex, routeCount)
            .mapNotNull { fraction -> routePointAtFraction(route, fraction) }

        val visibleCandidates = candidates.filter { it.isVisibleIn(visibleBounds) }
        val offscreenCandidates = candidates.filterNot { it.isVisibleIn(visibleBounds) }

        return visibleCandidates.firstOrNull { !it.overlapsAny(placedPositions) }
            ?: visibleCandidates.farthestFrom(placedPositions)
            ?: offscreenCandidates.firstOrNull { !it.overlapsAny(placedPositions) }
            ?: offscreenCandidates.farthestFrom(placedPositions)
    }

    private fun routePointAtFraction(route: Route, fraction: Double): LatLng? {
        val points = route.geometry.map { LatLng(it.lat, it.lng) }
        if (points.isEmpty()) {
            return null
        }
        if (points.size == 1) {
            return points.first()
        }

        val segmentDistances = points.zipWithNext { start, end -> start.fastDistanceTo(end) }
        val totalDistance = segmentDistances.sum()
        if (totalDistance == 0.0) {
            return points[points.size / 2]
        }

        val targetDistance = totalDistance * fraction.coerceIn(0.0, 1.0)
        var traversedDistance = 0.0
        segmentDistances.forEachIndexed { index, segmentDistance ->
            val nextDistance = traversedDistance + segmentDistance
            if (targetDistance <= nextDistance) {
                val segmentFraction = if (segmentDistance == 0.0) {
                    0.0
                } else {
                    (targetDistance - traversedDistance) / segmentDistance
                }
                return interpolate(points[index], points[index + 1], segmentFraction)
            }
            traversedDistance = nextDistance
        }

        return points.last()
    }

    private fun routeLabelCandidateFractions(routeIndex: Int, routeCount: Int): List<Double> {
        val preferredFraction = fallbackRouteLabelFraction(routeIndex, routeCount)
        return listOf(
            preferredFraction,
            0.30,
            0.70,
            0.45,
            0.58,
            0.18,
            0.82
        ).distinct()
    }

    private fun fallbackRouteLabelFraction(routeIndex: Int, routeCount: Int): Double {
        return if (routeCount <= 1) {
            0.5
        } else {
            (routeIndex + 1).toDouble() / (routeCount + 1).toDouble()
        }
    }

    private fun LatLng.overlapsAny(positions: List<LatLng>): Boolean {
        return positions.any { position ->
            fastDistanceTo(position) < minLabelDistanceMeters
        }
    }

    private fun LatLng.isVisibleIn(bounds: RouteLabelBounds?): Boolean {
        return bounds?.contains(this) ?: true
    }

    private fun List<LatLng>.farthestFrom(positions: List<LatLng>): LatLng? {
        return maxByOrNull { candidate ->
            positions.minOfOrNull { placed -> candidate.fastDistanceTo(placed) } ?: Double.MAX_VALUE
        }
    }

    private fun interpolate(start: LatLng, end: LatLng, fraction: Double): LatLng {
        return LatLng(
            latitude = start.latitude + (end.latitude - start.latitude) * fraction,
            longitude = start.longitude + (end.longitude - start.longitude) * fraction
        )
    }

    private companion object {
        const val MIN_ROUTE_LABEL_DISTANCE_METERS = 180.0
    }
}
