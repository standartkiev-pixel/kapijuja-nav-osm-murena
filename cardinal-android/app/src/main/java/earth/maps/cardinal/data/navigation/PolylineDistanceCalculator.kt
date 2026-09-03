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

package earth.maps.cardinal.data.navigation

import earth.maps.cardinal.data.LatLng
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.hypot

class PolylineDistanceCalculator @Inject constructor() {

    private var lastSegmentIndex = 0

    fun distanceFromPolyline(
        point: LatLng,
        polyline: List<LatLng>
    ): Double {
        if (polyline.size < MIN_POLYLINE_POINTS) return Double.MAX_VALUE

        val previousIndex = lastSegmentIndex

        var minDistance = Double.MAX_VALUE
        var bestIndex = previousIndex

        val start = maxOf(0, previousIndex - SEARCH_WINDOW)
        val end = minOf(polyline.size - 2, previousIndex + SEARCH_WINDOW)

        // 1) Windowed search (fast path)
        for (i in start..end) {
            val d = distanceToSegmentMeters(point, polyline[i], polyline[i + 1])

            if (d < minDistance) {
                minDistance = d
                bestIndex = i

                if (minDistance < EARLY_EXIT_DISTANCE_METERS) {
                    lastSegmentIndex = bestIndex
                    return minDistance
                }
            }
        }

        // 2) Fallback conditions

        // A) Edge detection → likely missed correct segment
        val isNearWindowEdge =
            bestIndex <= start + EDGE_MARGIN ||
                    bestIndex >= end - EDGE_MARGIN

        // B) Movement jump → GPS jumped far from previous segment
        val jumped = kotlin.math.abs(bestIndex - previousIndex) > SEARCH_WINDOW

        // C) Distance too large → obvious mismatch
        val isDistanceLarge = minDistance > FALLBACK_TRIGGER_DISTANCE_METERS

        val shouldFallback = jumped || isNearWindowEdge || isDistanceLarge

        // 3) Full scan fallback (rare but critical)
        if (shouldFallback) {
            var globalMin = minDistance
            var globalBestIndex = bestIndex

            for (i in 0 until polyline.size - 1) {
                val d = distanceToSegmentMeters(point, polyline[i], polyline[i + 1])
                if (d < globalMin) {
                    globalMin = d
                    globalBestIndex = i
                }
            }

            minDistance = globalMin
            bestIndex = globalBestIndex
        }

        lastSegmentIndex = bestIndex
        return minDistance
    }

    /*
     * Accurate distance using local projection (meters)
     */
    private fun distanceToSegmentMeters(p: LatLng, a: LatLng, b: LatLng): Double {

        val latRad = Math.toRadians(p.latitude)

        val ax = projectX(a.longitude, latRad)
        val ay = projectY(a.latitude)

        val bx = projectX(b.longitude, latRad)
        val by = projectY(b.latitude)

        val px = projectX(p.longitude, latRad)
        val py = projectY(p.latitude)

        val dx = bx - ax
        val dy = by - ay

        if (dx == ZERO && dy == ZERO) {
            return hypot(px - ax, py - ay)
        }

        val t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
        val clamped = t.coerceIn(ZERO, ONE)

        val projX = ax + clamped * dx
        val projY = ay + clamped * dy

        return hypot(px - projX, py - projY)
    }

    /*
     * Longitude → meters (scaled by latitude)
     */
    private fun projectX(lon: Double, latRad: Double): Double {
        return lon * cos(latRad) * METERS_PER_DEGREE
    }

    /*
     * Latitude → meters
     */
    private fun projectY(lat: Double): Double {
        return lat * METERS_PER_DEGREE
    }

    fun resetCache() {
        lastSegmentIndex = 0
    }

    companion object {
        private const val FALLBACK_TRIGGER_DISTANCE_METERS = 50.0
        private const val EDGE_MARGIN = 2
        private const val METERS_PER_DEGREE = 111_320.0
        private const val MIN_POLYLINE_POINTS = 2

        private const val SEARCH_WINDOW = 10
        private const val EARLY_EXIT_DISTANCE_METERS = 5.0

        private const val ZERO = 0.0
        private const val ONE = 1.0
    }
}
