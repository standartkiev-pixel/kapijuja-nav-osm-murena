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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stadiamaps.ferrostar.core.FerrostarCore
import dagger.hilt.android.lifecycle.HiltViewModel
import earth.maps.cardinal.data.ConnectivityRepository
import earth.maps.cardinal.data.TrafficRerouteSuggestion
import earth.maps.cardinal.routing.FerrostarWrapper
import earth.maps.cardinal.routing.FerrostarWrapperRepository
import earth.maps.cardinal.routing.TrafficEtaCalibration
import earth.maps.cardinal.routing.TrafficRouteSegments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.TripState
import javax.inject.Inject

data class TurnByTurnNavigationState(
    val activeRoute: Route? = null,
    val trafficAvailable: Boolean = false,
    val lastTrafficRefreshMillis: Long? = null,
    val etaCorrectionFactor: Double = TrafficEtaCalibration.NO_CORRECTION_FACTOR,
    val rerouteSuggestion: TrafficRerouteSuggestion? = null,
    val isInternetConnected: Boolean = true
)

@HiltViewModel
class TurnByTurnNavigationViewModel @Inject constructor(
    val ferrostarWrapperRepository: FerrostarWrapperRepository,
    private val connectivityRepository: ConnectivityRepository
) : ViewModel() {
    private val _state = MutableStateFlow(TurnByTurnNavigationState())
    val state = _state.asStateFlow()

    private var trafficMonitorJob: Job? = null
    private var activeWrapper: FerrostarWrapper? = null
    private var activeCore: FerrostarCore? = null
    private var suppressSuggestionsUntilMillis = 0L

    init {
        viewModelScope.launch {
            var wasInternetConnected = connectivityRepository.isInternetConnected.value
            connectivityRepository.isInternetConnected.collect { isConnected ->
                _state.update { it.copy(isInternetConnected = isConnected) }
                if (isConnected && !wasInternetConnected) {
                    val wrapper = activeWrapper
                    val navigationCore = activeCore
                    if (wrapper != null && navigationCore != null) {
                        wrapper.reprocessLastKnownLocation(navigationCore)
                    }
                }
                wasInternetConnected = isConnected
            }
        }
    }

    fun onEvent(uiEvent: TurnByTurnNavigationUiEvent) {
        when (uiEvent) {
            TurnByTurnNavigationUiEvent.OnStartNavigation -> ferrostarWrapperRepository.onStartNavigation()
            TurnByTurnNavigationUiEvent.OnStopNavigation -> ferrostarWrapperRepository.onStopNavigation()
        }
    }

    fun startNavigation(wrapper: FerrostarWrapper, route: Route) {
        val navigationCore = wrapper.core
        activeWrapper = wrapper
        activeCore = navigationCore
        onEvent(TurnByTurnNavigationUiEvent.OnStartNavigation)
        navigationCore.startNavigation(route = route)
        _state.value = TurnByTurnNavigationState(
            activeRoute = route,
            trafficAvailable = wrapper.isUsingTrafficProfile,
            lastTrafficRefreshMillis = System.currentTimeMillis(),
            etaCorrectionFactor = wrapper.etaCorrectionFactor,
            isInternetConnected = connectivityRepository.isInternetConnected.value
        )
        startTrafficMonitor(wrapper, navigationCore)
    }

    fun stopNavigation() {
        trafficMonitorJob?.cancel()
        trafficMonitorJob = null
        activeCore?.stopNavigation()
        activeCore = null
        activeWrapper = null
        _state.value = TurnByTurnNavigationState(
            isInternetConnected = connectivityRepository.isInternetConnected.value
        )
        onEvent(TurnByTurnNavigationUiEvent.OnStopNavigation)
    }

    fun acceptReroute() {
        val wrapper = activeWrapper ?: return
        val navigationCore = activeCore ?: return
        val suggestion = state.value.rerouteSuggestion ?: return
        navigationCore.replaceRoute(suggestion.route)
        _state.update {
            it.copy(
                activeRoute = suggestion.route,
                trafficAvailable = it.trafficAvailable ||
                    TrafficRouteSegments.trafficAvailable(suggestion.route),
                rerouteSuggestion = null,
                lastTrafficRefreshMillis = System.currentTimeMillis(),
                etaCorrectionFactor = wrapper.etaCorrectionFactor
            )
        }
    }

    fun dismissReroute() {
        suppressSuggestionsUntilMillis =
            System.currentTimeMillis() + TrafficRouteMonitor.DISMISS_SUPPRESSION_MILLIS
        _state.update { it.copy(rerouteSuggestion = null) }
    }

    private fun startTrafficMonitor(wrapper: FerrostarWrapper, navigationCore: FerrostarCore) {
        trafficMonitorJob?.cancel()
        if (!wrapper.isUsingTrafficProfile) {
            return
        }

        trafficMonitorJob = viewModelScope.launch {
            var lastRefreshMillis = System.currentTimeMillis()
            var lastRefreshLocation: GeographicCoordinate? = null

            while (isActive) {
                delay(TRAFFIC_MONITOR_CHECK_INTERVAL_MILLIS)
                val navigationState = navigationCore.state.value
                val tripState = navigationState.tripState as? TripState.Navigating ?: continue
                val currentLocation = tripState.snappedUserLocation.coordinates
                val nowMillis = System.currentTimeMillis()

                if (!connectivityRepository.isInternetConnected.value) {
                    continue
                }

                if (!TrafficRouteMonitor.shouldRefresh(
                        nowMillis = nowMillis,
                        lastRefreshMillis = lastRefreshMillis,
                        currentLocation = currentLocation,
                        lastRefreshLocation = lastRefreshLocation
                    )
                ) {
                    continue
                }

                val trafficRoutes = runCatching {
                    withContext(Dispatchers.IO) {
                        wrapper.getRoutesForNavigationRefresh(
                            initialLocation = tripState.snappedUserLocation,
                            waypoints = tripState.remainingWaypoints
                        )
                    }
                }.getOrElse { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                    lastRefreshMillis = nowMillis
                    lastRefreshLocation = currentLocation
                    _state.update { state ->
                        state.copy(lastTrafficRefreshMillis = nowMillis)
                    }
                    continue
                }

                val evaluation = TrafficRouteMonitor.evaluateRoutes(
                    currentRemainingGeometry = tripState.remainingSteps.flatMap { it.geometry },
                    currentDurationRemainingSeconds = tripState.progress.durationRemaining,
                    candidateRoutes = trafficRoutes.routes,
                    nowMillis = nowMillis,
                    suppressSuggestionsUntilMillis = suppressSuggestionsUntilMillis,
                    etaCorrectionFactor = wrapper.etaCorrectionFactor
                )

                evaluation.replacementRoute?.let { replacementRoute ->
                    navigationCore.replaceRoute(replacementRoute)
                    _state.update {
                        it.copy(
                            activeRoute = replacementRoute,
                            trafficAvailable = trafficRoutes.trafficAvailable,
                            lastTrafficRefreshMillis = nowMillis,
                            etaCorrectionFactor = wrapper.etaCorrectionFactor,
                            rerouteSuggestion = null
                        )
                    }
                } ?: _state.update {
                    it.copy(
                        lastTrafficRefreshMillis = nowMillis,
                        trafficAvailable = trafficRoutes.trafficAvailable,
                        etaCorrectionFactor = wrapper.etaCorrectionFactor,
                        rerouteSuggestion = evaluation.suggestion
                    )
                }

                lastRefreshMillis = nowMillis
                lastRefreshLocation = currentLocation
            }
        }
    }

    override fun onCleared() {
        trafficMonitorJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TRAFFIC_MONITOR_CHECK_INTERVAL_MILLIS = 30_000L
    }
}
