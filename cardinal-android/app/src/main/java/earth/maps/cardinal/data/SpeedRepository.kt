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

package earth.maps.cardinal.data

import android.location.Location
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class SpeedRepository @Inject constructor(
    private val speedEstimator: SpeedEstimator
) {
    private val _speedFlow: MutableStateFlow<UserSpeed?> = MutableStateFlow(null)
    val speedFlow: StateFlow<UserSpeed?> = _speedFlow.asStateFlow()

    private var previousLocation: Location? = null

    fun publishLocation(location: Location) {
        _speedFlow.value = speedEstimator
            .estimateMetersPerSecond(location, previousLocation)
            ?.let { speed ->
                UserSpeed(
                    metersPerSecond = speed,
                    timestampMillis = location.timestampMillis()
                )
            }
        previousLocation = location
    }

    fun reset() {
        _speedFlow.value = null
        previousLocation = null
    }

    private fun Location.timestampMillis(): Long {
        return time.takeIf { it > 0L } ?: System.currentTimeMillis()
    }
}
