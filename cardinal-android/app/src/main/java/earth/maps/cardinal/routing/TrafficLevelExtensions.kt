package earth.maps.cardinal.routing

import android.content.Context
import androidx.core.content.ContextCompat
import earth.maps.cardinal.R

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
private const val RGB_MASK = 0xFFFFFF

internal val TrafficLevel.colorRes: Int
    get() = when (this) {
        TrafficLevel.FREE -> R.color.traffic_free
        TrafficLevel.MODERATE -> R.color.traffic_moderate
        TrafficLevel.HEAVY -> R.color.traffic_heavy
        TrafficLevel.SEVERE -> R.color.traffic_severe
        TrafficLevel.UNKNOWN -> R.color.traffic_unknown
    }

internal fun TrafficLevel.navigationHexColor(context: Context): String {
    val color = ContextCompat.getColor(context, colorRes)
    return String.format("#%06X", color and RGB_MASK)
}
