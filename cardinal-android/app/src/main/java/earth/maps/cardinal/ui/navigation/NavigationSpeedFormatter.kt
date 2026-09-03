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

import com.stadiamaps.ferrostar.core.measurement.MeasurementSpeed
import com.stadiamaps.ferrostar.core.measurement.MeasurementSpeedUnit
import earth.maps.cardinal.data.AppPreferences
import earth.maps.cardinal.data.GeoUtils
import earth.maps.cardinal.data.UserSpeed
import kotlin.math.roundToInt

data class NavigationSpeedUi(
    val label: NavigationSpeedLabel,
    val valueText: String,
    val unitText: String
) {
    val displayText: String
        get() = "$valueText $unitText"
}

enum class NavigationSpeedLabel {
    SPEED,
    LIMIT
}

internal const val CURRENT_SPEED_STALE_MILLIS = 5_000L

internal fun UserSpeed?.toCurrentSpeedUi(
    distanceUnit: Int,
    nowMillis: Long
): NavigationSpeedUi? {
    val speed = this ?: return null
    if (nowMillis - speed.timestampMillis >= CURRENT_SPEED_STALE_MILLIS) {
        return null
    }

    return NavigationSpeedUi(
        label = NavigationSpeedLabel.SPEED,
        valueText = GeoUtils.formatSpeedValue(speed.metersPerSecond, distanceUnit),
        unitText = GeoUtils.speedUnitLabel(distanceUnit)
    )
}

internal fun MeasurementSpeed?.toSpeedLimitUi(distanceUnit: Int): NavigationSpeedUi? {
    val speedLimit = this ?: return null
    val unit = if (distanceUnit == AppPreferences.DISTANCE_UNIT_IMPERIAL) {
        MeasurementSpeedUnit.MilesPerHour
    } else {
        MeasurementSpeedUnit.KilometersPerHour
    }

    return NavigationSpeedUi(
        label = NavigationSpeedLabel.LIMIT,
        valueText = speedLimit.value(unit).roundToInt().toString(),
        unitText = GeoUtils.speedUnitLabel(distanceUnit)
    )
}
