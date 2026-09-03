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
    private val routeRepository: RouteRepository,
    private val appPreferenceRepository: AppPreferenceRepository,
    private val transitousService: TransitousService,
    recentSearchRepository: RecentSearchRepository,
    private val routeStateRepository: RouteStateRepository,
    private val planStateRepository: PlanStateRepository,
) : BaseSearchViewModel(geocodingService, viewportRepository, recentSearchRepository) {

    // Directions state
    var fromPlace by mutableStateOf<Place?>(null)
        private set

    var toPlace by mutableStateOf<Place?>(null)
        private set

    var selectedRoutingMode by mutableStateOf(RoutingMode.AUTO)
        private set

    var selectedRoutingProfile by mutableStateOf<RoutingProfile?>(null)
        private set

    // Expose state from repositories
    val routeState: StateFlow<RouteState> = routeStateRepository.routeState
    val planState: StateFlow<TransitPlanState> = planStateRepository.planState

    // Saved places for quick suggestions
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
        // Wait for FerrostarWrapperRepository to be initialized before setting options
        ferrostarWrapperRepository.awaitInitialization()

        // Set initial routing mode from preferences
        selectedRoutingMode = appPreferenceRepository.lastRoutingMode.value.let { modeString ->
            RoutingMode.entries.find { it.value == modeString } ?: RoutingMode.AUTO
        }
        // Initialize with the default profile for the current routing mode
        initializeDefaultProfileForMode(selectedRoutingMode)
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
            // Wait for FerrostarWrapperRepository to be initialized
            ferrostarWrapperRepository.awaitInitialization()

            routeStateRepository.setLoading(true)

            try {
                val ferrostarWrapper = getFerrostarWrapper()
                val waypoints = createWaypoints(destination)
                val userLocation = createUserLocation(origin)

                val routes = withContext(Dispatchers.IO) {
                    ferrostarWrapper.getRoutesWithTrafficFallback(userLocation, waypoints)
                }

                routeStateRepository.setRoutes(
                    routes = routes.routes,
                    trafficAvailable = routes.trafficAvailable,
                    lastTrafficRefreshMillis = if (routes.trafficAvailable) {
                        System.currentTimeMillis()
                    } else {
                        null
                    },
                    etaCorrectionFactor = ferrostarWrapper.etaCorrectionFactor
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
        RoutingMode.PEDESTRIAN -> ferrostarWrapperRepository.walking
        RoutingMode.BICYCLE -> ferrostarWrapperRepository.cycling
        else -> ferrostarWrapperRepository.driving
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
        // Load the default profile for the new mode
        initializeDefaultProfileForMode(mode)
        fetchDirectionsIfNeeded()
    }

    /**
     * Ensures the selected routing mode is valid by checking if it's in the available modes list.
     * If not, falls back to AUTO mode. This should be called when available modes change.
     */
    private suspend fun ensureSelectedModeIsValid(availableModes: List<RoutingMode>) {
        if (selectedRoutingMode !in availableModes) {
            // Fall back to AUTO mode if current mode is no longer available
            updateRoutingMode(RoutingMode.AUTO)
        }
    }

    fun selectRoutingProfile(profile: RoutingProfile?) {
        selectedRoutingProfile = profile

        viewModelScope.launch {
            // Apply profile options to the ferrostar wrapper
            if (profile != null) {
                // Use custom routing profile - update options on existing wrapper
                val profileWithOptions = routingProfileRepository.getProfileWithOptions(profile.id)
                profileWithOptions.fold(
                    onSuccess = { pair ->
                        pair?.let { (_, options) ->
                            // Update options on the appropriate wrapper
                            options?.let {
                                ferrostarWrapperRepository.setOptionsForMode(
                                    selectedRoutingMode, it
                                )
                            }
                        }
                    },
                    onFailure = {
                        // Fallback to default if profile loading fails
                        ferrostarWrapperRepository.resetOptionsToDefaultsForMode(selectedRoutingMode)
                    }
                )
            } else {
                // User explicitly selected "Default" - use built-in defaults
                selectedRoutingProfile = null
                ferrostarWrapperRepository.resetOptionsToDefaultsForMode(selectedRoutingMode)
            }

            fetchDirectionsIfNeeded()
        }

    }

    /**
     * Gets available routing profiles for the current routing mode.
     */
    fun getAvailableProfilesForCurrentMode() =
        routingProfileRepository.getProfilesForMode(selectedRoutingMode)

    /**
     * Gets available routing modes for display in the UI.
     * Always includes AUTO, PUBLIC_TRANSPORT, PEDESTRIAN, and BICYCLE.
     * Conditionally includes TRUCK, MOTOR_SCOOTER, and MOTORCYCLE only if custom profiles exist for those modes.
     */
    fun getAvailableRoutingModes() = combine(
        routingProfileRepository.getProfilesForMode(RoutingMode.TRUCK),
        routingProfileRepository.getProfilesForMode(RoutingMode.MOTOR_SCOOTER),
        routingProfileRepository.getProfilesForMode(RoutingMode.MOTORCYCLE)
    ) { truckProfiles, motorScooterProfiles, motorcycleProfiles ->
        val modes = mutableListOf(
            RoutingMode.AUTO,
            RoutingMode.PUBLIC_TRANSPORT,
            RoutingMode.PEDESTRIAN,
            RoutingMode.BICYCLE,
        )

        // Add conditional modes only if they have custom profiles
        if (truckProfiles.isNotEmpty()) {
            modes.add(RoutingMode.TRUCK)
        }
        if (motorScooterProfiles.isNotEmpty()) {
            modes.add(RoutingMode.MOTOR_SCOOTER)
        }
        if (motorcycleProfiles.isNotEmpty()) {
            modes.add(RoutingMode.MOTORCYCLE)
        }

        // Ensure the selected mode is still valid
        ensureSelectedModeIsValid(modes)

        modes
    }

    /**
     * Initializes the default routing profile for the given mode.
     * This loads the saved default profile from the database and applies its options.
     * If no default profile exists, sets selectedRoutingProfile to null and uses built-in defaults.
     */
    private suspend fun initializeDefaultProfileForMode(mode: RoutingMode) {
        routingProfileRepository.getDefaultProfile(mode).fold(
            onSuccess = { profileWithOptions ->
                if (profileWithOptions != null) {
                    val (profile, options) = profileWithOptions
                    options?.let {
                        selectedRoutingProfile = profile
                        ferrostarWrapperRepository.setOptionsForMode(mode, it)
                    }
                } else {
                    // No default profile exists, use built-in defaults
                    selectedRoutingProfile = null
                    ferrostarWrapperRepository.resetOptionsToDefaultsForMode(mode)
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to load default profile for mode $mode", error)
                // Fallback to built-in defaults
                selectedRoutingProfile = null
                ferrostarWrapperRepository.resetOptionsToDefaultsForMode(mode)
            }
        )
    }


    fun createTurnByTurnRoute(state: RouteState): CardinalRoute.TurnByTurnNavigation? =
        state.routes.getOrNull(state.selectedRouteIndex ?: 0)?.let { route ->
            CardinalRoute.TurnByTurnNavigation(
                routeId = routeRepository.storeRoute(route),
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
        // Use fromPlace as focus point for viewport biasing if available,
        // otherwise fall back to current viewport center
        return fromPlace?.latLng ?: super.getSearchFocusPoint()
    }

    /**
     * Gets current location as a Place, handling loading state.
     */
    suspend fun getCurrentLocationAsPlace(): Place? {
        isGettingLocation = true
        return try {
            locationRepository.getCurrentLocationAsPlace()
        } finally {
            isGettingLocation = false
        }
    }

    /**
     * Called when a search result is selected from directions search.
     * Adds the place to recent searches.
     */
    fun onPlaceSelectedFromSearch(place: Place) {
        addRecentSearch(place)
    }

    /**
     * Selects a route by index from the available routes.
     * This method is called when a route annotation is tapped on the map.
     * 
     * @param routeIndex The index of the route to select. Use -1 for the current selected route.
     */
    fun selectRouteByIndex(routeIndex: Int) {
        val currentRoutes = routeState.value.routes
        if (currentRoutes.isNotEmpty()) {
            val actualIndex = if (routeIndex == -1) {
                // If -1 is passed, it means the current selected route was tapped
                // Keep the current selection
                routeState.value.selectedRouteIndex ?: 0
            } else {
                // Convert the reversed index back to the correct index
                // because routes are displayed in reverse order in the RouteLayer
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
