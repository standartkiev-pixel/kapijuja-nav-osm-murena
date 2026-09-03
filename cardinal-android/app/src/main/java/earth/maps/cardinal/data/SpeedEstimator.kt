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

class SpeedEstimator @Inject constructor() {

    fun estimateMetersPerSecond(
        location: Location,
        previousLocation: Location?
    ): Double? {
        return location.providerSpeedMetersPerSecond()
            ?: location.derivedSpeedMetersPerSecond(previousLocation)
    }

    private fun Location.providerSpeedMetersPerSecond(): Double? {
        if (!hasSpeed()) {
            return null
        }

        return speed.toDouble().normalizedSpeedMetersPerSecond()
    }

    private fun Location.derivedSpeedMetersPerSecond(previousLocation: Location?): Double? {
        val previous = previousLocation ?: return null
        val elapsedMillis = time - previous.time
        if (elapsedMillis !in MIN_DERIVED_SPEED_INTERVAL_MS..MAX_DERIVED_SPEED_INTERVAL_MS) {
            return null
        }

        val speed = previous.distanceTo(this) / (elapsedMillis / 1000.0)
        return speed.normalizedSpeedMetersPerSecond()
    }

    private fun Double.normalizedSpeedMetersPerSecond(): Double? {
        if (!isFinite() || this < 0.0 || this > MAX_REASONABLE_SPEED_METERS_PER_SECOND) {
            return null
        }

        return if (this < MIN_MOVEMENT_SPEED_METERS_PER_SECOND) {
            0.0
        } else {
            this
        }
    }

    private companion object {
        private const val MIN_DERIVED_SPEED_INTERVAL_MS = 500L
        private const val MAX_DERIVED_SPEED_INTERVAL_MS = 10_000L
        private const val MIN_MOVEMENT_SPEED_METERS_PER_SECOND = 0.3
        private const val MAX_REASONABLE_SPEED_METERS_PER_SECOND = 150.0
    }
}
