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
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteDeviation
import uniffi.ferrostar.RouteDeviationDetector
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.UserLocation

class FerrostarRouteDeviationDetector(
    private val deviationDetector: DeviationDetector
) : RouteDeviationDetector {

    override fun checkRouteDeviation(
        location: UserLocation,
        route: Route,
        currentRouteStep: RouteStep
    ): RouteDeviation {

        val raw = LatLng(
            location.coordinates.lat,
            location.coordinates.lng
        )

        val geometry = route.geometry.map {
            LatLng(it.lat, it.lng)
        }

        return if (deviationDetector.isOffRoute(raw, geometry)) {
            RouteDeviation.OffRoute(DEVIATION_FROM_ROUTE_LINE)
        } else {
            RouteDeviation.NoDeviation
        }
    }

    companion object {
        private const val DEVIATION_FROM_ROUTE_LINE = 30.0
    }
}
