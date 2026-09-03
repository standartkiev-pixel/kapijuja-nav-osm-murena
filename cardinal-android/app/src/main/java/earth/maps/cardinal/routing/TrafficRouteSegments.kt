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

import com.stadiamaps.ferrostar.core.annotation.Speed
import com.stadiamaps.ferrostar.core.annotation.SpeedUnit
import com.stadiamaps.ferrostar.core.annotation.valhalla.ValhallaOSRMExtendedAnnotation
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep

enum class TrafficLevel {
    FREE,
    MODERATE,
    HEAVY,
    SEVERE,
    UNKNOWN
}

data class TrafficSegmentUi(
    val coordinates: List<GeographicCoordinate>,
    val level: TrafficLevel
)

object TrafficRouteSegments {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun trafficAvailable(route: Route): Boolean =
        trafficAvailable(route.steps)

    fun trafficAvailable(steps: List<RouteStep>): Boolean =
        steps.any { step ->
            step.annotations.orEmpty().any { parseAnnotation(it) != null }
        }

    fun build(route: Route): List<TrafficSegmentUi> = build(route.steps)

    fun build(steps: List<RouteStep>): List<TrafficSegmentUi> {
        val segments = mutableListOf<TrafficSegmentUi>()
        steps.forEach { step ->
            val coordinatePairs = step.geometry.zipWithNext()
            coordinatePairs.forEachIndexed { index, pair ->
                val annotation = step.annotations.orEmpty().getOrNull(index)?.let(::parseAnnotation)
                segments.add(
                    TrafficSegmentUi(
                        coordinates = listOf(pair.first, pair.second),
                        level = annotation?.toTrafficLevel() ?: TrafficLevel.UNKNOWN
                    )
                )
            }
        }
        return segments
    }

    fun parseAnnotation(rawAnnotation: String): ValhallaOSRMExtendedAnnotation? {
        return try {
            json.decodeFromString(
                ValhallaOSRMExtendedAnnotation.serializer(),
                rawAnnotation
            )
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun trafficLevelFor(annotation: ValhallaOSRMExtendedAnnotation): TrafficLevel {
        val speedMetersPerSecond = speedMetersPerSecond(annotation) ?: return TrafficLevel.UNKNOWN

        val speedLimitMetersPerSecond = annotation.speedLimit?.metersPerSecond()
        val ratio = speedLimitMetersPerSecond?.takeIf { it > 0.0 }?.let {
            speedMetersPerSecond / it
        }

        return when {
            ratio != null && ratio >= 0.75 -> TrafficLevel.FREE
            ratio != null && ratio >= 0.50 -> TrafficLevel.MODERATE
            ratio != null && ratio >= 0.25 -> TrafficLevel.HEAVY
            ratio != null -> TrafficLevel.SEVERE
            speedMetersPerSecond >= 15.0 -> TrafficLevel.FREE
            speedMetersPerSecond >= 8.0 -> TrafficLevel.MODERATE
            speedMetersPerSecond >= 3.0 -> TrafficLevel.HEAVY
            else -> TrafficLevel.SEVERE
        }
    }

    fun speedMetersPerSecond(annotation: ValhallaOSRMExtendedAnnotation): Double? =
        annotation.speed ?: annotation.distance?.let { distance ->
            annotation.duration?.takeIf { it > 0.0 }?.let { duration -> distance / duration }
        }

    private fun ValhallaOSRMExtendedAnnotation.toTrafficLevel(): TrafficLevel =
        trafficLevelFor(this)

    private fun Speed.metersPerSecond(): Double? = when (this) {
        is Speed.Value -> when (unit) {
            SpeedUnit.KILOMETERS_PER_HOUR -> value / 3.6
            SpeedUnit.MILES_PER_HOUR -> value * 0.44704
            SpeedUnit.KNOTS -> value * 0.514444
        }

        Speed.NoLimit,
        Speed.Unknown -> null
    }
}
