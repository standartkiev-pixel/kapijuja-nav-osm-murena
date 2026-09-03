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

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import earth.maps.cardinal.R.drawable
import earth.maps.cardinal.data.parseRouteColor
import earth.maps.cardinal.transit.Mode
import earth.maps.cardinal.ui.util.defaultTransitModeColor

@Composable
internal fun TransitTimelineLegUi.routeTimelineColor(): Color =
    transitRouteTimelineColor(mode = mode, routeColor = routeColor)

@Composable
internal fun transitRouteTimelineColor(mode: Mode, routeColor: String?): Color {
    return if (mode == Mode.WALK) {
        MaterialTheme.colorScheme.outline
    } else {
        parseRouteColor(routeColor) ?: mode.defaultTransitModeColor()
    }
}

internal fun Mode.transitIcon(): Int {
    return when (this) {
        Mode.WALK -> drawable.mode_walk
        Mode.BIKE -> drawable.mode_bike
        Mode.CAR, Mode.CAR_PARKING, Mode.CAR_DROPOFF -> drawable.mode_car
        Mode.BUS -> drawable.ic_bus_railway
        Mode.TRAM -> drawable.ic_bus_railway
        Mode.SUBWAY -> drawable.ic_subway_walk
        Mode.RAIL, Mode.HIGHSPEED_RAIL, Mode.REGIONAL_RAIL, Mode.REGIONAL_FAST_RAIL -> drawable.ic_bus_railway
        Mode.FERRY -> drawable.ic_bus_railway
        Mode.AIRPLANE -> drawable.ic_bus_railway
        else -> drawable.mode_walk
    }
}
