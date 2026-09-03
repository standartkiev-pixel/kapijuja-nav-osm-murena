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

import earth.maps.cardinal.data.TrafficRerouteSuggestion
import earth.maps.cardinal.routing.TrafficEtaCalibration
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class TrafficRouteEvaluation(
    val replacementRoute: Route? = null,
    val suggestion: TrafficRerouteSuggestion? = null
)

object TrafficRouteMonitor {
    const val REFRESH_INTERVAL_MILLIS = 2 * 60 * 1000L
    const val REFRESH_DISTANCE_METERS = 500.0
    const val SAME_PATH_MIN_ETA_CHANGE_SECONDS = 90
    const val SAME_PATH_MIN_ETA_CHANGE_RATIO = 0.05
    const val MIN_TIME_SAVINGS_SECONDS = 3 * 60
    const val MIN_TIME_SAVINGS_RATIO = 0.10
    const val DISMISS_SUPPRESSION_MILLIS = 5 * 60 * 1000L

    fun evaluateRoutes(
        currentRemainingGeometry: List<GeographicCoordinate>,
        currentDurationRemainingSeconds: Double,
        candidateRoutes: List<Route>,
        nowMillis: Long,
        suppressSuggestionsUntilMillis: Long,
        etaCorrectionFactor: Double = TrafficEtaCalibration.NO_CORRECTION_FACTOR
    ): TrafficRouteEvaluation {
        val samePathRoute = candidateRoutes.firstOrNull { candidate ->
            isSamePath(currentRemainingGeometry, candidate.geometry)
        }
        if (samePathRoute != null) {
            return if (
                hasMeaningfulEtaChange(
                    currentDurationRemainingSeconds = currentDurationRemainingSeconds,
                    candidateRoute = samePathRoute,
                    etaCorrectionFactor = etaCorrectionFactor
                )
            ) {
                TrafficRouteEvaluation(replacementRoute = samePathRoute)
            } else {
                TrafficRouteEvaluation()
            }
        }

        if (nowMillis < suppressSuggestionsUntilMillis) {
            return TrafficRouteEvaluation()
        }

        val fasterRoute = candidateRoutes
            .minByOrNull {
                TrafficEtaCalibration.correctedRouteDurationSeconds(it, etaCorrectionFactor)
            }
            ?: return TrafficRouteEvaluation()
        val correctedCurrentDuration = TrafficEtaCalibration.correctedDurationSeconds(
            rawDurationSeconds = currentDurationRemainingSeconds,
            correctionFactor = etaCorrectionFactor
        )
        val correctedFasterDuration = TrafficEtaCalibration.correctedRouteDurationSeconds(
            route = fasterRoute,
            correctionFactor = etaCorrectionFactor
        )
        val timeSavings = correctedCurrentDuration - correctedFasterDuration
        val savingsRatio = timeSavings / correctedCurrentDuration.coerceAtLeast(1.0)

        return if (
            timeSavings >= MIN_TIME_SAVINGS_SECONDS &&
            savingsRatio >= MIN_TIME_SAVINGS_RATIO
        ) {
            TrafficRouteEvaluation(
                suggestion = TrafficRerouteSuggestion(
                    route = fasterRoute,
                    timeSavingsSeconds = timeSavings.roundToInt()
                )
            )
        } else {
            TrafficRouteEvaluation()
        }
    }

    fun shouldRefresh(
        nowMillis: Long,
        lastRefreshMillis: Long,
        currentLocation: GeographicCoordinate,
        lastRefreshLocation: GeographicCoordinate?
    ): Boolean {
        if (nowMillis - lastRefreshMillis >= REFRESH_INTERVAL_MILLIS) {
            return true
        }
        return lastRefreshLocation?.let {
            distanceMeters(it, currentLocation) >= REFRESH_DISTANCE_METERS
        } ?: true
    }

    fun Route.durationSeconds(): Double = steps.sumOf { it.duration }

    fun remainingGeometry(route: Route): List<GeographicCoordinate> =
        route.steps.flatMap { it.geometry }.ifEmpty { route.geometry }

    private fun hasMeaningfulEtaChange(
        currentDurationRemainingSeconds: Double,
        candidateRoute: Route,
        etaCorrectionFactor: Double
    ): Boolean {
        val correctedCurrentDuration = TrafficEtaCalibration.correctedDurationSeconds(
            rawDurationSeconds = currentDurationRemainingSeconds,
            correctionFactor = etaCorrectionFactor
        )
        val correctedCandidateDuration = TrafficEtaCalibration.correctedRouteDurationSeconds(
            route = candidateRoute,
            correctionFactor = etaCorrectionFactor
        )
        val etaDifference = kotlin.math.abs(correctedCurrentDuration - correctedCandidateDuration)
        val etaDifferenceRatio = etaDifference / correctedCurrentDuration.coerceAtLeast(1.0)

        return etaDifference >= SAME_PATH_MIN_ETA_CHANGE_SECONDS ||
            etaDifferenceRatio >= SAME_PATH_MIN_ETA_CHANGE_RATIO
    }

    private fun isSamePath(
        currentGeometry: List<GeographicCoordinate>,
        candidateGeometry: List<GeographicCoordinate>
    ): Boolean {
        if (currentGeometry.size < 2 || candidateGeometry.size < 2) {
            return false
        }
        val currentDestination = currentGeometry.last()
        val candidateDestination = candidateGeometry.last()
        if (distanceMeters(currentDestination, candidateDestination) > 50.0) {
            return false
        }

        val samples = listOf(0.25, 0.5, 0.75)
        return samples.all { fraction ->
            val current = currentGeometry[(fraction * (currentGeometry.lastIndex)).toInt()]
            val candidate = candidateGeometry[(fraction * (candidateGeometry.lastIndex)).toInt()]
            distanceMeters(current, candidate) <= 75.0
        }
    }

    private fun distanceMeters(
        first: GeographicCoordinate,
        second: GeographicCoordinate
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val dLat = Math.toRadians(second.lat - first.lat)
        val dLng = Math.toRadians(second.lng - first.lng)
        val lat1 = Math.toRadians(first.lat)
        val lat2 = Math.toRadians(second.lat)

        val a = sin(dLat / 2).pow(2.0) +
            cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadiusMeters * c
    }
}
