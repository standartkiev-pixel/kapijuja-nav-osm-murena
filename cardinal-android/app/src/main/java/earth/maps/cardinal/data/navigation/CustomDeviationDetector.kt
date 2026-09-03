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
import earth.maps.cardinal.domain.DeviationDetector

class CustomDeviationDetector(
    private val distanceCalculator: PolylineDistanceCalculator,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : DeviationDetector {

    private var lastRouteHash: Int = 0
    private var pendingOffRouteSinceMillis: Long? = null
    private var isConfirmedOffRoute = false

    override fun isOffRoute(
        location: LatLng,
        route: List<LatLng>
    ): Boolean {

        if (route.size < MIN_ROUTE_POINTS) return false

        val currentHash = route.hashCode()
        if (currentHash != lastRouteHash) {
            resetDeviationState()
            distanceCalculator.resetCache()
            lastRouteHash = currentHash
        }

        val distance = distanceCalculator.distanceFromPolyline(location, route)

        if (isConfirmedOffRoute) {
            return if (distance > RETURN_TO_ROUTE_THRESHOLD_METERS) {
                true
            } else {
                resetDeviationState()
                false
            }
        }

        if (distance <= OFF_ROUTE_THRESHOLD_METERS) {
            pendingOffRouteSinceMillis = null
            return false
        }

        val now = currentTimeMillis()
        val pendingSince = pendingOffRouteSinceMillis ?: now.also {
            pendingOffRouteSinceMillis = it
        }
        isConfirmedOffRoute = now - pendingSince >= OFF_ROUTE_DEBOUNCE_MS
        return isConfirmedOffRoute
    }

    private fun resetDeviationState() {
        pendingOffRouteSinceMillis = null
        isConfirmedOffRoute = false
    }

    companion object {
        private const val OFF_ROUTE_THRESHOLD_METERS = 15.0
        private const val RETURN_TO_ROUTE_THRESHOLD_METERS = 8.0
        private const val OFF_ROUTE_DEBOUNCE_MS = 1500L
        private const val MIN_ROUTE_POINTS = 2
    }
}
