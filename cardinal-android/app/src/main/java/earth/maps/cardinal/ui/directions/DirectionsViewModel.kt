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

package earth.maps.cardinal.ui.directions

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.PlanStateRepository
import earth.maps.cardinal.data.RouteState
import earth.maps.cardinal.data.RouteStateRepository
import earth.maps.cardinal.data.RoutingMode
import earth.maps.cardinal.data.RoutingProfileSelectionStore
import earth.maps.cardinal.data.TransitPlanState
import earth.maps.cardinal.data.ViewportRepository
import earth.maps.cardinal.data.room.RecentSearchRepository
import earth.maps.cardinal.data.room.RoutingProfile
import earth.maps.cardinal.data.room.RoutingProfileRepository
import earth.maps.cardinal.data.room.SavedPlaceDao
import earth.maps.cardinal.data.room.SavedPlaceRepository
import earth.maps.cardinal.geocoding.GeocodingService
import earth.maps.cardinal.routing.FerrostarWrapperRepository
import earth.maps.cardinal.routing.RouteRepository
import earth.maps.cardinal.transit.TransitousService
import earth.maps.cardinal.ui.core.BaseSearchViewModel
import earth.maps.cardinal.ui.core.CardinalRoute
import foundation.e.lib.telemetry.Telemetry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind
import java.time.Instant
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@HiltViewModel
class DirectionsViewModel @Inject constructor(
    geocodingService: GeocodingService,
    private val ferrostarWrapperRepository: FerrostarWrapperRepository,
    viewportRepository: ViewportRepository,
    private val placeDao: SavedPlaceDao,
    private val savedPlaceRepository: SavedPlaceRepository,
    private val locationRepository: LocationRepository,
    private val routingProfileRepository: RoutingProfileRepository,
    private val routingProfileSelectionStore: RoutingProfileSelectionStore,
    private val routeRepository: RouteRepository,
    private val appPreferenceRepository: AppPreferenceRepository,
    private val transitousService: TransitousService,
    recentSearchRepository: RecentSearchRepository,
    private val routeStateRepository: RouteStateRepository,
    private val planStateRepository: PlanStateRepository,
) : BaseSearchViewModel(geocodingService, viewportRepository, recentSearchRepository) {

    var fromPlace by mutableStateOf<Place?>(null)
        private set

    var toPlace by mutableStateOf<Place?>(null)
        private set

    var selectedRoutingMode by mutableStateOf(RoutingMode.AUTO)
        private set

    var selectedRoutingProfile by mutableStateOf<RoutingProfile?>(null)
        private set

    val routeState: StateFlow<RouteState> = routeStateRepository.routeState
    val planState: StateFlow<TransitPlanState> = planStateRepository.planState

    val savedPlaces = placeDao.getAllPlacesAsFlow().map { list ->
        list.map { savedPlaceRepository.toPlace(it) }
    }

    override suspend fun getPinnedSearchPlaces(): List<Place> {
        return savedPlaceRepository.gePinnedPlacesForSearch()
    }

    var isGettingLocation by mutableStateOf(false)
        private set

    private var haveManuallySetDeparture: Boolean = false

    suspend fun initializeRoutingMode() {
        ferrostarWrapperRepository.awaitInitialization()

        selectedRoutingMode = appPreferenceRepository.lastRoutingMode.value.let { modeString ->
            RoutingMode.entries.find { it.value == modeString } ?: RoutingMode.AUTO
        }
        initializeProfileForMode(selectedRoutingMode)
    }

    suspend fun initializeDeparture() {
        if (!appPreferenceRepository.continuousLocationTracking.value) {
            return
        }
        val defaultDeparture = getCurrentLocationAsPlace()
        defaultDeparture?.let {
            if (!haveManuallySetDeparture) {
                updateFromPlace(it)
            }
        }
    }

    fun updateFromPlace(place: Place?) {
        haveManuallySetDeparture = true
        fromPlace = place
        fetchDirectionsIfNeeded()
    }

    fun updateToPlace(place: Place?) {
        toPlace = place
        fetchDirectionsIfNeeded()
    }

    private fun fetchDirectionsIfNeeded() {
        val origin = fromPlace
        val destination = toPlace
        if (origin != null && destination != null) {
            fetchDirections(origin, destination)
        } else {
            routeStateRepository.clear()
            planStateRepository.clear()
        }
    }

    private fun fetchDirections(origin: Place, destination: Place) {
        if (selectedRoutingMode == RoutingMode.PUBLIC_TRANSPORT) {
            fetchTransitDirections(origin, destination)
        } else {
            fetchDrivingDirections(origin, destination)
        }
    }

    private fun fetchDrivingDirections(origin: Place, destination: Place) {
        viewModelScope.launch {
            planStateRepository.clear()
            ferrostarWrapperRepository.awaitInitialization()
            routeStateRepository.setLoading(true)

            try {
                val ferrostarWrapper = getFerrostarWrapper()
                val waypoints = createWaypoints(destination)
                val userLocation = createUserLocation(origin)

                val routes = withContext(Dispatchers.IO) {
                    ferrostarWrapper.getRoutesWithNearestDestinationFirst(userLocation, waypoints)
                }

                val accessApproach = routes.routes.firstOrNull()?.let { strictRoute ->
                    withContext(Dispatchers.IO) {
                        try {
                            ferrostarWrapper.getAccessApproachRoute(
                                strictRoute = strictRoute,
                                requestedDestination = GeographicCoordinate(
                                    destination.latLng.latitude,
                                    destination.latLng.longitude
                                )
                            )
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Log.w(TAG, "Heavy-vehicle access approach was unavailable", error)
                            null
                        }
                    }
                }

                routeStateRepository.setRoutes(
                    routes = routes.routes,
                    trafficAvailable = routes.trafficAvailable,
                    lastTrafficRefreshMillis = if (routes.trafficAvailable) {
                        System.currentTimeMillis()
                    } else {
                        null
                    },
                    etaCorrectionFactor = ferrostarWrapper.etaCorrectionFactor,
                    accessApproach = accessApproach
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error while fetching route", e)
                routeStateRepository.setDirectionError(e.toRouteError())
                Telemetry.reportException(e = e)
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun fetchTransitDirections(origin: Place, destination: Place) {
        viewModelScope.launch {
            routeStateRepository.clear()
            planStateRepository.clear()
            planStateRepository.setLoading(true)

            try {
                transitousService.getPlan(
                    from = LatLng(origin.latLng.latitude, origin.latLng.longitude),
                    to = LatLng(destination.latLng.latitude, destination.latLng.longitude),
                    withFares = true,
                ).collect { planResponse ->
                    if (planResponse.direct.isEmpty() && planResponse.itineraries.isEmpty()) {
                        planStateRepository.setDirectionError(DirectionUiError.RouteNotFound)
                    } else {
                        planStateRepository.setPlanResponse(planResponse)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error while fetching transit plan", e)
                planStateRepository.setDirectionError(e.toRouteError())
                Telemetry.reportException(e = e)
            }
        }
    }

    private fun getFerrostarWrapper() = when (selectedRoutingMode) {
        RoutingMode.AUTO -> ferrostarWrapperRepository.driving
        RoutingMode.TRUCK -> ferrostarWrapperRepository.truck
        RoutingMode.BUS -> ferrostarWrapperRepository.bus
        RoutingMode.MOTOR_SCOOTER -> ferrostarWrapperRepository.motorScooter
        RoutingMode.MOTORCYCLE -> ferrostarWrapperRepository.motorcycle
        RoutingMode.PEDESTRIAN -> ferrostarWrapperRepository.walking
        RoutingMode.BICYCLE -> ferrostarWrapperRepository.cycling
        RoutingMode.PUBLIC_TRANSPORT -> ferrostarWrapperRepository.driving
    }

    private fun createWaypoints(destination: Place) = listOf(
        Waypoint(
            coordinate = GeographicCoordinate(
                destination.latLng.latitude,
                destination.latLng.longitude
            ),
            kind = WaypointKind.BREAK
        )
    )

    private fun createUserLocation(origin: Place) = UserLocation(
        coordinates = GeographicCoordinate(origin.latLng.latitude, origin.latLng.longitude),
        horizontalAccuracy = 10.0,
        courseOverGround = null,
        timestamp = Instant.now(),
        speed = null
    )

    suspend fun updateRoutingMode(mode: RoutingMode) {
        selectedRoutingMode = mode
        appPreferenceRepository.setLastRoutingMode(mode.value)
        initializeProfileForMode(mode)
        fetchDirectionsIfNeeded()
    }

    private suspend fun ensureSelectedModeIsValid(availableModes: List<RoutingMode>) {
        if (selectedRoutingMode !in availableModes) {
            updateRoutingMode(RoutingMode.AUTO)
        }
    }

    fun selectRoutingProfile(profile: RoutingProfile?) {
        selectedRoutingProfile = profile

        viewModelScope.launch {
            if (profile != null) {
                routingProfileSelectionStore.save(selectedRoutingMode, profile.id)
                applyProfile(selectedRoutingMode, profile)
            } else {
                routingProfileSelectionStore.save(selectedRoutingMode, null)
                selectedRoutingProfile = null
                ferrostarWrapperRepository.resetOptionsToDefaultsForMode(selectedRoutingMode)
            }
            fetchDirectionsIfNeeded()
        }
    }

    fun getAvailableProfilesForCurrentMode() =
        routingProfileRepository.getProfilesForMode(selectedRoutingMode)

    fun getAvailableRoutingModes() = combine(
        routingProfileRepository.getProfilesForMode(RoutingMode.TRUCK),
        routingProfileRepository.getProfilesForMode(RoutingMode.BUS),
        routingProfileRepository.getProfilesForMode(RoutingMode.MOTOR_SCOOTER),
        routingProfileRepository.getProfilesForMode(RoutingMode.MOTORCYCLE)
    ) { truckProfiles, busProfiles, motorScooterProfiles, motorcycleProfiles ->
        val modes = mutableListOf(
            RoutingMode.AUTO,
            RoutingMode.PUBLIC_TRANSPORT,
            RoutingMode.PEDESTRIAN,
            RoutingMode.BICYCLE,
        )

        if (truckProfiles.isNotEmpty()) {
            modes.add(RoutingMode.TRUCK)
        }
        if (busProfiles.isNotEmpty()) {
            modes.add(RoutingMode.BUS)
        }
        if (motorScooterProfiles.isNotEmpty()) {
            modes.add(RoutingMode.MOTOR_SCOOTER)
        }
        if (motorcycleProfiles.isNotEmpty()) {
            modes.add(RoutingMode.MOTORCYCLE)
        }

        ensureSelectedModeIsValid(modes)
        modes
    }

    /**
     * Uses built-in defaults only when the mode has no custom profiles.
     * If custom profiles exist, restores the driver's last choice; then falls back
     * to the database default, and finally the most recently updated profile.
     */
    private suspend fun initializeProfileForMode(mode: RoutingMode) {
        val profiles = routingProfileRepository.getProfilesForMode(mode).first()
        if (profiles.isEmpty()) {
            routingProfileSelectionStore.save(mode, null)
            selectedRoutingProfile = null
            ferrostarWrapperRepository.resetOptionsToDefaultsForMode(mode)
            return
        }

        val lastSelectedId = routingProfileSelectionStore.load(mode)
        val selectedProfile = profiles.firstOrNull { it.id == lastSelectedId }
            ?: profiles.firstOrNull { it.isDefault }
            ?: profiles.first()

        routingProfileSelectionStore.save(mode, selectedProfile.id)
        applyProfile(mode, selectedProfile)
    }

    private suspend fun applyProfile(mode: RoutingMode, profile: RoutingProfile) {
        routingProfileRepository.getProfileWithOptions(profile.id).fold(
            onSuccess = { profileWithOptions ->
                val options = profileWithOptions?.second
                if (options != null) {
                    selectedRoutingProfile = profile
                    ferrostarWrapperRepository.setOptionsForMode(mode, options)
                } else {
                    Log.e(TAG, "Routing profile ${profile.id} has no usable options")
                    selectedRoutingProfile = null
                    ferrostarWrapperRepository.resetOptionsToDefaultsForMode(mode)
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to load routing profile ${profile.id} for mode $mode", error)
                selectedRoutingProfile = null
                ferrostarWrapperRepository.resetOptionsToDefaultsForMode(mode)
            }
        )
    }

    fun createTurnByTurnRoute(state: RouteState): CardinalRoute.TurnByTurnNavigation? =
        state.routes.getOrNull(state.selectedRouteIndex ?: 0)?.let { route ->
            CardinalRoute.TurnByTurnNavigation(
                routeId = routeRepository.storeRoute(
                    route = route,
                    accessApproach = state.accessApproach
                ),
                routingMode = selectedRoutingMode
            )
        }

    fun flipDestinations() {
        val tempFrom = fromPlace
        val tempTo = toPlace
        fromPlace = tempTo
        toPlace = tempFrom
        fetchDirectionsIfNeeded()
    }

    fun recalculateDirections() {
        val origin = fromPlace
        val destination = toPlace
        if (origin != null && destination != null) {
            if (origin.isMyLocation || destination.isMyLocation) {
                refreshMyLocationPlacesAndRecalculate(origin, destination)
            } else {
                fetchDirections(origin, destination)
            }
        }
    }

    private fun refreshMyLocationPlacesAndRecalculate(origin: Place, destination: Place) {
        viewModelScope.launch {
            isGettingLocation = true
            try {
                val updatedOrigin = if (origin.isMyLocation) {
                    locationRepository.getFreshCurrentLocationAsPlace()?.also { fromPlace = it }
                        ?: origin
                } else origin

                val updatedDestination = if (destination.isMyLocation) {
                    locationRepository.getFreshCurrentLocationAsPlace()?.also { toPlace = it }
                        ?: destination
                } else destination

                fetchDirections(updatedOrigin, updatedDestination)
            } catch (_: Exception) {
                fetchDirections(origin, destination)
            } finally {
                isGettingLocation = false
            }
        }
    }

    override suspend fun getSearchFocusPoint(): LatLng? {
        return fromPlace?.latLng ?: super.getSearchFocusPoint()
    }

    suspend fun getCurrentLocationAsPlace(): Place? {
        isGettingLocation = true
        return try {
            locationRepository.getCurrentLocationAsPlace()
        } finally {
            isGettingLocation = false
        }
    }

    fun onPlaceSelectedFromSearch(place: Place) {
        addRecentSearch(place)
    }

    fun selectRouteByIndex(routeIndex: Int) {
        val currentRoutes = routeState.value.routes
        if (currentRoutes.isNotEmpty()) {
            val actualIndex = if (routeIndex == -1) {
                routeState.value.selectedRouteIndex ?: 0
            } else {
                currentRoutes.size - 1 - routeIndex
            }

            if (actualIndex >= 0 && actualIndex < currentRoutes.size) {
                routeStateRepository.selectRoute(actualIndex)
            }
        }
    }

    fun selectRoute(selectedIndex: Int) {
        routeStateRepository.selectRoute(selectedIndex)
    }

    fun selectTransitItinerary(selectedIndex: Int) {
        planStateRepository.selectItinerary(selectedIndex)
    }

    companion object {
        private const val TAG = "DirectionsViewModel"
    }
}
