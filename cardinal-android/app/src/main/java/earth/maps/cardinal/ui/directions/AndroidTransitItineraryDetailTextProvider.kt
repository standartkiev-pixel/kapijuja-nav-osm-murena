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

import android.content.Context
import earth.maps.cardinal.R.plurals
import earth.maps.cardinal.R.string
import earth.maps.cardinal.transit.Mode

internal class AndroidTransitItineraryDetailTextProvider(
    private val context: Context
) : TransitItineraryDetailTextProvider {
    override fun distanceNotAvailable(): String =
        context.getString(string.distance_not_available)

    override fun transferCount(count: Int): String =
        context.resources.getQuantityString(plurals.transit_transfer_count, count, count)

    override fun walkOverview(walkingDistanceText: String, speedSuffix: String): String =
        context.getString(string.transit_walk_overview, walkingDistanceText, speedSuffix)

    override fun destination(): String =
        context.getString(string.transit_destination)

    override fun stopFallback(): String =
        context.getString(string.transit_stop_fallback)

    override fun platform(platform: String): String =
        context.getString(string.transit_platform, platform)

    override fun walkSegment(durationText: String, distanceSuffix: String, waitSuffix: String): String =
        context.getString(string.transit_walk_segment, durationText, distanceSuffix, waitSuffix)

    override fun headsign(headsign: String): String =
        context.getString(string.transit_to_headsign, headsign)

    override fun rideSummary(stopCount: Int, durationText: String, distanceSuffix: String): String {
        val stopText = when (stopCount) {
            0 -> context.getString(string.transit_ride)
            else -> context.resources.getQuantityString(plurals.transit_ride_stop_count, stopCount, stopCount)
        }
        return "$stopText ($durationText)$distanceSuffix"
    }

    override fun waitUpTo(durationText: String): String =
        context.getString(string.transit_wait_up_to, durationText)

    override fun cancelled(): String =
        context.getString(string.transit_cancelled)

    override fun onTime(): String =
        context.getString(string.transit_on_time)

    override fun delayed(durationText: String): String =
        context.getString(string.transit_delayed, durationText)

    override fun modeName(mode: Mode): String =
        context.getString(mode.transitModeNameString())
}

internal fun Mode.transitModeNameString(): Int {
    return when (this) {
        Mode.WALK -> string.transit_mode_walk
        Mode.BIKE -> string.transit_mode_bike
        Mode.CAR,
        Mode.CAR_PARKING,
        Mode.CAR_DROPOFF -> string.transit_mode_car
        Mode.BUS,
        Mode.COACH -> string.transit_mode_bus
        Mode.TRAM -> string.transit_mode_tram
        Mode.SUBWAY,
        Mode.METRO -> string.transit_mode_subway
        Mode.FUNICULAR,
        Mode.CABLE_CAR,
        Mode.AREAL_LIFT -> string.transit_mode_cable_car
        Mode.RAIL,
        Mode.HIGHSPEED_RAIL,
        Mode.LONG_DISTANCE,
        Mode.NIGHT_RAIL,
        Mode.REGIONAL_RAIL,
        Mode.REGIONAL_FAST_RAIL -> string.transit_mode_rail
        Mode.FERRY -> string.transit_mode_ferry
        Mode.AIRPLANE -> string.transit_mode_airplane
        Mode.RENTAL -> string.transit_mode_rental
        Mode.ODM,
        Mode.FLEX -> string.transit_mode_flexible
        Mode.TRANSIT,
        Mode.OTHER -> string.transit_mode_transit
    }
}
