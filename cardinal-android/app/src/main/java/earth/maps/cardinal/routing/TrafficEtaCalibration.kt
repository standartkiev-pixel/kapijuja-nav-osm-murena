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

package earth.maps.cardinal.routing

import uniffi.ferrostar.Route
import kotlin.math.roundToInt

object TrafficEtaCalibration {
    const val NO_CORRECTION_FACTOR = 1.0
    const val AUTO_TRAFFIC_FACTOR = 1.15
    const val AUTO_TRAFFIC_PROFILE = "auto_traffic_premium"

    fun factorForProfile(
        profile: ValhallaCostingProfile,
        trafficAvailable: Boolean
    ): Double = if (trafficAvailable && profile.routeProviderProfile == AUTO_TRAFFIC_PROFILE) {
        AUTO_TRAFFIC_FACTOR
    } else {
        NO_CORRECTION_FACTOR
    }

    fun correctedDurationSeconds(
        rawDurationSeconds: Double,
        correctionFactor: Double
    ): Double = rawDurationSeconds * correctionFactor.coerceAtLeast(NO_CORRECTION_FACTOR)

    fun correctedDurationSecondsInt(
        rawDurationSeconds: Double,
        correctionFactor: Double
    ): Int = correctedDurationSeconds(rawDurationSeconds, correctionFactor).roundToInt()

    fun rawRouteDurationSeconds(route: Route): Double = route.steps.sumOf { it.duration }

    fun correctedRouteDurationSeconds(
        route: Route,
        correctionFactor: Double
    ): Double = correctedDurationSeconds(rawRouteDurationSeconds(route), correctionFactor)

    fun correctedRouteDurationSecondsInt(
        route: Route,
        correctionFactor: Double
    ): Int = correctedDurationSecondsInt(rawRouteDurationSeconds(route), correctionFactor)
}
