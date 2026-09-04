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

package earth.maps.cardinal.ui.core

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.times
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.maps.cardinal.bottomsheet.BottomSheetScaffoldState
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.RoutingMode
import earth.maps.cardinal.data.StableTransitItineraryIdentityPolicy
import earth.maps.cardinal.transit.Itinerary
import earth.maps.cardinal.ui.directions.DirectionsViewModel
import earth.maps.cardinal.ui.directions.RouteDisplayHandler

@Composable
internal fun DirectionsRouteMapOrchestrator(
    state: AppContentState,
    route: CardinalRoute.Directions,
    topOfBackStack: CardinalRoute,
    viewModel: DirectionsViewModel,
    appPreferences: AppPreferenceRepository,
    transitOverlayCoordinator: TransitMapOverlayCoordinator,
    polylinePadding: PaddingValues
) {
    ApplyInitialDirectionsPlaces(route = route, viewModel = viewModel)

    RouteDisplayHandler(
        viewModel = viewModel,
        cameraState = state.cameraState,
        appPreferences = appPreferences,
        padding = polylinePadding,
        onRouteUpdate = {
                routeUpdate,
                allRoutes,
                trafficAvailable,
                etaCorrectionFactor,
                accessApproachRoute ->
            state.currentRoute = routeUpdate
            state.allRoutes = allRoutes
            state.trafficAvailable = trafficAvailable
            state.etaCorrectionFactor = etaCorrectionFactor
            state.accessApproachRoute = accessApproachRoute
        }
    )

    ObserveSelectedTransitItinerary(
        coordinator = transitOverlayCoordinator,
        viewModel = viewModel,
        ownsTransitOverlay = topOfBackStack == route,
        polylinePadding = polylinePadding,
        animationDuration = appPreferences.animationSpeedDurationValue
    )
    ObserveSelectedRouteIndex(state = state, viewModel = viewModel)
    ClearDirectionsRouteStateOnDispose(state = state)
}

@Composable
internal fun rememberTransitItineraryDetailMapOrchestrator(
    state: AppContentState,
    itinerary: Itinerary?,
    appPreferences: AppPreferenceRepository,
    transitOverlayCoordinator: TransitMapOverlayCoordinator,
    scaffoldState: BottomSheetScaffoldState
): (Int) -> Unit {
    if (itinerary == null) {
        return {}
    }

    val routeCoroutineScope = rememberCoroutineScope()
    val transitFocusJob = remember(itinerary) {
        TransitCameraFocusJob()
    }
    val animationDuration = appPreferences.animationSpeedDurationValue
    val initialFitPadding = state.transitDetailInitialFitPadding()
    val focusedLegPadding = state.transitDetailFocusedLegPadding()

    DisposableEffect(transitFocusJob) {
        onDispose {
            transitFocusJob.cancel()
        }
    }

    LaunchedEffect(key1 = Unit) {
        scaffoldState.bottomSheetState.collapse()
    }

    LaunchedEffect(itinerary, initialFitPadding, animationDuration) {
        transitOverlayCoordinator.enterDetailMode(itinerary)

        transitFocusJob.launchLatest(routeCoroutineScope) {
            transitOverlayCoordinator.fitItinerary(
                itinerary = itinerary,
                padding = initialFitPadding,
                duration = animationDuration
            )
        }
    }

    DisposableEffect(key1 = Unit) {
        onDispose {
            transitOverlayCoordinator.clearOverlay()
        }
    }

    return remember(
        itinerary,
        routeCoroutineScope,
        transitFocusJob,
        transitOverlayCoordinator,
        scaffoldState,
        focusedLegPadding,
        animationDuration
    ) {
        { legIndex ->
            transitFocusJob.launchLatest(routeCoroutineScope) {
                transitOverlayCoordinator.focusLeg(
                    itinerary = itinerary,
                    legIndex = legIndex,
                    padding = focusedLegPadding,
                    duration = animationDuration,
                    beforeCameraAnimation = {
                        scaffoldState.bottomSheetState.collapse()
                    }
                )
            }
        }
    }
}

@Composable
internal fun ClearTransitMapStateOutsideTransitRoutes(
    coordinator: TransitMapOverlayCoordinator,
    topOfBackStack: CardinalRoute
) {
    LaunchedEffect(topOfBackStack) {
        if (!coordinator.ownsOverlay(topOfBackStack)) {
            coordinator.clearOverlay()
        }
    }
}

private fun AppContentState.transitDetailInitialFitPadding(): PaddingValues =
    PaddingValues(
        start = screenWidthDp / 8,
        top = screenHeightDp / 8,
        end = screenWidthDp / 8,
        bottom = min(
            3f * screenHeightDp / 4,
            peekHeight + screenHeightDp / 8
        )
    )

private fun AppContentState.transitDetailFocusedLegPadding(): PaddingValues =
    PaddingValues(
        start = screenWidthDp / 8,
        top = screenHeightDp / 8,
        end = screenWidthDp / 8,
        bottom = screenHeightDp / 3
    )

@Composable
private fun ApplyInitialDirectionsPlaces(
    route: CardinalRoute.Directions,
    viewModel: DirectionsViewModel
) {
    LaunchedEffect(route) {
        route.fromPlace?.let(viewModel::updateFromPlace)
        viewModel.updateToPlace(route.toPlace)
    }
}

@Composable
private fun ObserveSelectedTransitItinerary(
    coordinator: TransitMapOverlayCoordinator,
    viewModel: DirectionsViewModel,
    ownsTransitOverlay: Boolean,
    polylinePadding: PaddingValues,
    animationDuration: kotlin.time.Duration
) {
    val observation = rememberDirectionsTransitOverlayObservation(
        viewModel = viewModel,
        ownsTransitOverlay = ownsTransitOverlay
    )
    val autoFitState = remember { TransitItineraryAutoFitState() }

    RefreshDirectionsTransitOverlay(
        coordinator = coordinator,
        observation = observation
    )
    ApplyDirectionsTransitOverlayOwnership(
        coordinator = coordinator,
        observation = observation,
        autoFitState = autoFitState,
        polylinePadding = polylinePadding,
        animationDuration = animationDuration
    )
}

@Composable
private fun rememberDirectionsTransitOverlayObservation(
    viewModel: DirectionsViewModel,
    ownsTransitOverlay: Boolean
): DirectionsTransitOverlayObservation {
    val planState by viewModel.planState.collectAsStateWithLifecycle()
    val routingMode = viewModel.selectedRoutingMode
    val selectedTransitItinerary = planState.selectedItinerary
    val selectedTransitItineraryIdentity = selectedTransitItinerary
        ?.let(StableTransitItineraryIdentityPolicy::identityOf)
    val selectedItineraryFitKey = selectedTransitItineraryIdentity
        ?.let { identity -> "$identity#${planState.selectedItineraryIndex}" }

    return remember(
        ownsTransitOverlay,
        routingMode,
        selectedTransitItinerary,
        selectedItineraryFitKey
    ) {
        DirectionsTransitOverlayObservation(
            ownsTransitOverlay = ownsTransitOverlay,
            routingMode = routingMode,
            selectedItinerary = selectedTransitItinerary,
            selectedItineraryFitKey = selectedItineraryFitKey
        )
    }
}

private data class DirectionsTransitOverlayObservation(
    val ownsTransitOverlay: Boolean,
    val routingMode: RoutingMode,
    val selectedItinerary: Itinerary?,
    val selectedItineraryFitKey: String?
) {
    val isPublicTransport: Boolean
        get() = routingMode == RoutingMode.PUBLIC_TRANSPORT
}

@Composable
private fun RefreshDirectionsTransitOverlay(
    coordinator: TransitMapOverlayCoordinator,
    observation: DirectionsTransitOverlayObservation
) {
    LaunchedEffect(
        observation.ownsTransitOverlay,
        observation.routingMode,
        observation.selectedItinerary
    ) {
        if (!observation.ownsTransitOverlay) {
            return@LaunchedEffect
        }

        if (!observation.isPublicTransport) {
            return@LaunchedEffect
        }

        coordinator.refreshDirectionsTransitItinerary(observation.selectedItinerary)
    }
}

@Composable
private fun ApplyDirectionsTransitOverlayOwnership(
    coordinator: TransitMapOverlayCoordinator,
    observation: DirectionsTransitOverlayObservation,
    autoFitState: TransitItineraryAutoFitState,
    polylinePadding: PaddingValues,
    animationDuration: kotlin.time.Duration
) {
    LaunchedEffect(
        observation.ownsTransitOverlay,
        observation.routingMode,
        observation.selectedItineraryFitKey
    ) {
        if (!observation.ownsTransitOverlay) {
            return@LaunchedEffect
        }

        if (!observation.isPublicTransport) {
            coordinator.clearOverlay()
            autoFitState.reset()
            return@LaunchedEffect
        }

        coordinator.enterDirectionsTransitMode(observation.selectedItinerary)
        val fitKey = observation.selectedItineraryFitKey
        if (fitKey == null) {
            autoFitState.reset()
            return@LaunchedEffect
        }

        if (autoFitState.shouldFit(fitKey)) {
            coordinator.fitItinerary(
                itinerary = observation.selectedItinerary,
                padding = polylinePadding,
                duration = animationDuration
            )
            autoFitState.markFitted(fitKey)
        }
    }
}

internal class TransitItineraryAutoFitState {
    private var lastFittedItineraryIdentity: String? = null

    fun shouldFit(itineraryIdentity: String): Boolean =
        lastFittedItineraryIdentity != itineraryIdentity

    fun markFitted(itineraryIdentity: String) {
        lastFittedItineraryIdentity = itineraryIdentity
    }

    fun reset() {
        lastFittedItineraryIdentity = null
    }
}

@Composable
private fun ObserveSelectedRouteIndex(
    state: AppContentState,
    viewModel: DirectionsViewModel
) {
    LaunchedEffect(state.selectedRouteIndex) {
        val selectedIndex = state.selectedRouteIndex ?: return@LaunchedEffect
        if (selectedIndex in viewModel.routeState.value.routes.indices) {
            viewModel.selectRoute(selectedIndex)
        }
    }
}

@Composable
private fun ClearDirectionsRouteStateOnDispose(state: AppContentState) {
    DisposableEffect(key1 = Unit) {
        onDispose {
            state.currentRoute = null
            state.allRoutes = emptyList()
            state.trafficAvailable = false
            state.etaCorrectionFactor = 1.0
            state.accessApproachRoute = null
            state.selectedRouteIndex = null
        }
    }
}
