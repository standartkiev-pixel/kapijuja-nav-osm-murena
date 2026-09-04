/*
 *     Cardinal Maps
 *     Copyright (C) 2025 Cardinal Maps Authors
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

import androidx.annotation.VisibleForTesting
import earth.maps.cardinal.routing.HeavyVehicleAccessApproach
import earth.maps.cardinal.ui.directions.DirectionUiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uniffi.ferrostar.Route

data class TrafficRerouteSuggestion(
    val route: Route,
    val timeSavingsSeconds: Int
)

data class RouteState(
    val routes: List<Route> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedRouteIndex: Int? = null,
    val directionError: DirectionUiError? = null,
    val isTrafficAvailable: Boolean = false,
    val lastTrafficRefreshMs: Long? = null,
    val etaCorrectionFactor: Double = 1.0,
    val rerouteSuggestion: TrafficRerouteSuggestion? = null,
    val accessApproach: HeavyVehicleAccessApproach? = null,
)

class RouteStateRepository {
    private val _routeState = MutableStateFlow(RouteState())
    val routeState: StateFlow<RouteState> = _routeState.asStateFlow()

    fun setLoading(isLoading: Boolean) {
        _routeState.value = _routeState.value.copy(isLoading = isLoading)
    }

    fun setRoutes(
        routes: List<Route>,
        trafficAvailable: Boolean = _routeState.value.isTrafficAvailable,
        lastTrafficRefreshMillis: Long? = _routeState.value.lastTrafficRefreshMs,
        etaCorrectionFactor: Double = _routeState.value.etaCorrectionFactor,
        accessApproach: HeavyVehicleAccessApproach? = null
    ) {
        _routeState.value = _routeState.value.copy(
            routes = routes,
            isLoading = false,
            error = null,
            directionError = null,
            isTrafficAvailable = trafficAvailable,
            lastTrafficRefreshMs = lastTrafficRefreshMillis,
            etaCorrectionFactor = etaCorrectionFactor,
            rerouteSuggestion = null,
            accessApproach = accessApproach
        )
    }

    fun selectRoute(index: Int) {
        _routeState.value = _routeState.value.copy(selectedRouteIndex = index)
    }

    fun setError(error: String?) {
        _routeState.value = _routeState.value.copy(isLoading = false, error = error)
    }

    fun setDirectionError(directionUiError: DirectionUiError?) {
        _routeState.value = _routeState.value.copy(isLoading = false, directionError = directionUiError, error = null)
    }

    fun setRerouteSuggestion(suggestion: TrafficRerouteSuggestion?) {
        _routeState.value = _routeState.value.copy(rerouteSuggestion = suggestion)
    }

    fun clear() {
        _routeState.value = RouteState()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun setStateForTest(state: RouteState) {
        _routeState.value = state
    }
}
