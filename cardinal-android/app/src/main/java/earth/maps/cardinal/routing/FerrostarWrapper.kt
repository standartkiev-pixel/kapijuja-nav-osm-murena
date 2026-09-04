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

package earth.maps.cardinal.routing

import android.content.Context
import com.stadiamaps.ferrostar.composeui.notification.DefaultForegroundNotificationBuilder
import com.stadiamaps.ferrostar.core.CorrectiveAction
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import com.stadiamaps.ferrostar.core.LocationProvider
import com.stadiamaps.ferrostar.core.LocationUpdateListener
import com.stadiamaps.ferrostar.core.RouteDeviationHandler
import com.stadiamaps.ferrostar.core.SpokenInstructionObserver
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider
import com.stadiamaps.ferrostar.core.service.FerrostarForegroundServiceManager
import com.stadiamaps.ferrostar.core.service.ForegroundServiceManager
import com.stadiamaps.ferrostar.core.toUserLocation
import earth.maps.cardinal.data.ConnectivityRepository
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.OrientationRepository
import earth.maps.cardinal.data.RoutingMode
import earth.maps.cardinal.data.navigation.CustomDeviationDetector
import earth.maps.cardinal.data.navigation.FerrostarRouteDeviationDetector
import earth.maps.cardinal.data.navigation.PolylineDistanceCalculator
import earth.maps.cardinal.data.room.RoutingProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.Heading
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.ParsingException
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteAdapter
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.UserLocation
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WellKnownRouteProvider
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep
import java.net.HttpURLConnection
import java.util.concurrent.Executor
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.toJavaInstant

class FerrostarWrapper(
    context: Context,
    private val locationRepository: LocationRepository,
    val orientationRepository: OrientationRepository,
    private val mode: RoutingMode,
    private val localValhallaEndpoint: String,
    private val androidTtsObserver: SpokenInstructionObserver,
    private val routingProfileRepository: RoutingProfileRepository,
    routingOptions: RoutingOptions? = null,
    private val okHttpClient: OkHttpClient,
    private val connectivityRepository: ConnectivityRepository
) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var isTrafficEnabled = mode.supportsTraffic()

    val isUsingTrafficProfile: Boolean
        get() = isTrafficEnabled && mode.supportsTraffic()

    val routingMode: RoutingMode
        get() = mode

    val currentValhallaProfile: String
        get() = currentValhallaCostingProfile.routeProviderProfile

    private val currentValhallaCostingProfile: ValhallaCostingProfile
        get() = mode.valhallaCostingProfile(isTrafficEnabled, previousRouteOptions)

    val etaCorrectionFactor: Double
        get() = TrafficEtaCalibration.factorForProfile(
            profile = currentValhallaCostingProfile,
            trafficAvailable = isUsingTrafficProfile
        )

    private val foregroundServiceManager: ForegroundServiceManager =
        FerrostarForegroundServiceManager(
            context = context,
            DefaultForegroundNotificationBuilder(context)
        )

    @OptIn(ExperimentalTime::class)
    private val locationProvider = object : LocationProvider {
        private val listeners = mutableListOf<LocationUpdateListener>()

        override val lastLocation: UserLocation?
            get() = locationRepository.getLastLocation()?.toUserLocation()
        override val lastHeading: Heading?
            get() {
                val heading = ((orientationRepository.azimuth.value.toInt() + 360) % 360).toUShort()
                return Heading(
                    heading,
                    0u,
                    Clock.System.now().toJavaInstant()
                )
            }

        init {
            coroutineScope.launch {
                locationRepository.locationFlow.collect { location ->
                    listeners.forEach { listener ->
                        location?.toUserLocation()?.let { userLocation ->
                            listener.onLocationUpdated(userLocation)
                        }
                    }
                }
            }
        }

        override fun addListener(
            listener: LocationUpdateListener,
            executor: Executor
        ) {
            listeners.add(listener)
        }

        override fun removeListener(listener: LocationUpdateListener) {
            listeners.remove(listener)
        }
    }

    private var previousRouteOptions: RoutingOptions? =
        routingOptions ?: routingProfileRepository.createDefaultOptionsForMode(mode)

    var core = createCore(previousRouteOptions)

    init {
        core.spokenInstructionObserver = androidTtsObserver
    }

    /**
     * Updates the routing options by recreating the core with new options.
     * This allows changing routing behavior without creating a new wrapper instance.
     */
    fun setOptions(
        newRoutingOptions: RoutingOptions? = null,
    ) {
        val routingOptions = newRoutingOptions ?: previousRouteOptions
        previousRouteOptions = routingOptions
        core = createCore(routingOptions)
    }

    suspend fun getRoutesWithTrafficFallback(
        initialLocation: UserLocation,
        waypoints: List<Waypoint>
    ): TrafficRouteResult {
        return runTrafficFallbackRouteRequest(
            supportsTraffic = mode.supportsTraffic(),
            isTrafficEnabled = { isTrafficEnabled },
            setTrafficEnabled = ::setTrafficEnabled,
            fetchRoutes = {
                core.getRoutes(initialLocation, waypoints)
            }
        )
    }

    suspend fun getRoutesForNavigationRefresh(
        initialLocation: UserLocation,
        waypoints: List<Waypoint>
    ): TrafficRouteResult {
        var trafficEnabledForRequest = isTrafficEnabled
        return runTrafficFallbackRouteRequest(
            supportsTraffic = mode.supportsTraffic(),
            isTrafficEnabled = { trafficEnabledForRequest },
            setTrafficEnabled = { trafficEnabledForRequest = it },
            fetchRoutes = {
                createCore(
                    routingOptions = previousRouteOptions,
                    trafficEnabled = trafficEnabledForRequest,
                    includeForegroundServiceManager = false
                ).getRoutes(initialLocation, waypoints)
            }
        )
    }


    /**
     * Builds an optional dashed-access approach for Truck/Bus destinations that the strict
     * heavy-vehicle route can only reach by snapping to a nearby legal edge.
     *
     * The fallback uses AUTO access semantics with ignore_access=true, but preserves vehicle
     * dimensions/weight and keeps ignore_restrictions/ignore_oneways/ignore_closures false.
     * It is intentionally limited to a short final approach.
     */
    suspend fun getAccessApproachRoute(
        strictRoute: Route,
        requestedDestination: GeographicCoordinate
    ): Route? {
        if (mode != RoutingMode.TRUCK && mode != RoutingMode.BUS) {
            return null
        }

        val strictEnd = strictRoute.waypoints.lastOrNull()?.coordinate
            ?: strictRoute.geometry.lastOrNull()
            ?: return null
        val offsetMeters = strictEnd.distanceMetersTo(requestedDestination)

        if (offsetMeters < ACCESS_APPROACH_MIN_OFFSET_METERS ||
            offsetMeters > ACCESS_APPROACH_MAX_OFFSET_METERS
        ) {
            return null
        }

        val accessOptions = previousRouteOptions?.toHeavyVehicleAccessOptions()
            ?: routingProfileRepository.createDefaultOptionsForMode(mode)
                ?.toHeavyVehicleAccessOptions()
            ?: return null

        val accessCore = createCore(
            routingOptions = accessOptions,
            trafficEnabled = false,
            includeForegroundServiceManager = false,
            costingProfileOverride = ValhallaCostingProfile.Auto
        )

        val initialLocation = UserLocation(
            coordinates = strictEnd,
            horizontalAccuracy = 5.0,
            courseOverGround = null,
            timestamp = Clock.System.now().toJavaInstant(),
            speed = null
        )

        return accessCore.getRoutes(
            initialLocation = initialLocation,
            waypoints = listOf(
                Waypoint(
                    coordinate = requestedDestination,
                    kind = WaypointKind.BREAK
                )
            )
        ).firstOrNull()
    }

    fun reprocessLastKnownLocation(core: FerrostarCore = this.core) {
        locationProvider.lastLocation?.let(core::onLocationUpdated)
    }

    private fun setTrafficEnabled(enabled: Boolean) {
        if (isTrafficEnabled == enabled) {
            return
        }
        isTrafficEnabled = enabled
        core = createCore(previousRouteOptions)
    }

    private fun createCore(
        routingOptions: RoutingOptions?,
        trafficEnabled: Boolean = isTrafficEnabled,
        includeForegroundServiceManager: Boolean = true,
        costingProfileOverride: ValhallaCostingProfile? = null
    ): FerrostarCore {
        val costingProfile = costingProfileOverride
            ?: mode.valhallaCostingProfile(trafficEnabled, routingOptions)
        val profile = costingProfile.routeProviderProfile
        return FerrostarCore(
            routeAdapter = RouteAdapter.fromWellKnownRouteProvider(
                WellKnownRouteProvider.Valhalla(
                    endpointUrl = localValhallaEndpoint,
                    profile = profile,
                    optionsJson = routingOptions?.toValhallaOptionsJson(
                        costingProfileOverride = costingProfile,
                        departNow = trafficEnabled && mode.supportsTraffic()
                    )
                )
            ),
            httpClient = OkHttpClientProvider(okHttpClient),
            locationProvider = locationProvider,
            navigationControllerConfig = NavigationControllerConfig(
                waypointAdvance = WaypointAdvanceMode.WaypointWithinRange(WAYPOINT_ADVANCE_RANGE),
                stepAdvanceCondition = stepAdvanceDistanceEntryAndExit(
                    STEP_ADVANCE_DISTANCE_TO_END.toUShort(),
                    STEP_ADVANCE_DISTANCE_AFTER_END.toUShort(),
                    STEP_ADVANCE_MINIMUM_HORIZONTAL_ACCURACY.toUShort()
                ),
                arrivalStepAdvanceCondition = stepAdvanceDistanceToEndOfStep(
                    ARRIVAL_STEP_ADVANCE_DISTANCE.toUShort(),
                    ARRIVAL_STEP_ADVANCE_MINIMUM_HORIZONTAL_ACCURACY.toUShort()
                ),
                routeDeviationTracking = RouteDeviationTracking.Custom(
                    detector = getDeviationDetector()
                ),
                snappedLocationCourseFiltering = COURSE_FILTERING
            ),
            foregroundServiceManager = foregroundServiceManager.takeIf { includeForegroundServiceManager },
        ).also {
            it.spokenInstructionObserver = androidTtsObserver
            it.deviationHandler = RouteDeviationHandler { _, _, remainingWaypoints ->
                correctiveActionForConnectivity(
                    isInternetConnected = connectivityRepository.isInternetConnected.value,
                    remainingWaypoints = remainingWaypoints
                )
            }
        }
    }

    private fun getDeviationDetector(): FerrostarRouteDeviationDetector {
        val distanceCalculator = PolylineDistanceCalculator()
        val deviationDetector = CustomDeviationDetector(distanceCalculator)
        return FerrostarRouteDeviationDetector(deviationDetector)
    }

    companion object {
        const val WAYPOINT_ADVANCE_RANGE = 100.0
        const val STEP_ADVANCE_DISTANCE_TO_END = 30u
        const val STEP_ADVANCE_DISTANCE_AFTER_END = 5u
        const val STEP_ADVANCE_MINIMUM_HORIZONTAL_ACCURACY = 32u
        const val ARRIVAL_STEP_ADVANCE_DISTANCE = 30u
        const val ARRIVAL_STEP_ADVANCE_MINIMUM_HORIZONTAL_ACCURACY = 32u
        const val ROUTE_DEVIATION_MINIMUM_ACCURACY = 8u
        const val ROUTE_DEVIATION_MAX_DEVIATION = 25.0
        const val ACCESS_APPROACH_MIN_OFFSET_METERS = 8.0
        const val ACCESS_APPROACH_MAX_OFFSET_METERS = 120.0
        val COURSE_FILTERING = CourseFiltering.RAW
    }
}

private fun GeographicCoordinate.distanceMetersTo(other: GeographicCoordinate): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(lat)
    val lat2 = Math.toRadians(other.lat)
    val deltaLat = lat2 - lat1
    val deltaLng = Math.toRadians(other.lng - lng)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(lat1) * cos(lat2) * sin(deltaLng / 2) * sin(deltaLng / 2)
    return 2 * earthRadiusMeters * atan2(sqrt(a), sqrt(1 - a))
}

data class TrafficRouteResult(
    val routes: List<Route>,
    val trafficAvailable: Boolean
)

internal suspend fun runTrafficFallbackRouteRequest(
    supportsTraffic: Boolean,
    isTrafficEnabled: () -> Boolean,
    setTrafficEnabled: (Boolean) -> Unit,
    fetchRoutes: suspend () -> List<Route>
): TrafficRouteResult {
    if (supportsTraffic) {
        setTrafficEnabled(true)
    }

    return try {
        val routes = fetchRoutes()
        TrafficRouteResult(
            routes = routes,
            trafficAvailable = isTrafficEnabled() && supportsTraffic
        )
    } catch (e: InvalidStatusCodeException) {
        retryWithoutTrafficProfileIfPossible(
            supportsTraffic = supportsTraffic,
            isTrafficEnabled = isTrafficEnabled,
            setTrafficEnabled = setTrafficEnabled,
            fetchRoutes = fetchRoutes,
            cause = e
        )
    } catch (e: ParsingException.InvalidStatusCode) {
        retryWithoutTrafficProfileIfPossible(
            supportsTraffic = supportsTraffic,
            isTrafficEnabled = isTrafficEnabled,
            setTrafficEnabled = setTrafficEnabled,
            fetchRoutes = fetchRoutes,
            cause = e
        )
    }
}

private suspend fun retryWithoutTrafficProfileIfPossible(
    supportsTraffic: Boolean,
    isTrafficEnabled: () -> Boolean,
    setTrafficEnabled: (Boolean) -> Unit,
    fetchRoutes: suspend () -> List<Route>,
    cause: Exception
): TrafficRouteResult {
    if (!supportsTraffic || !isTrafficEnabled()) {
        throw cause
    }
    if (!cause.canRetryWithoutTrafficProfile()) {
        throw cause
    }

    setTrafficEnabled(false)
    return TrafficRouteResult(
        routes = fetchRoutes(),
        trafficAvailable = false
    )
}

private fun Exception.canRetryWithoutTrafficProfile(): Boolean {
    val statusCode = when (this) {
        is InvalidStatusCodeException -> statusCode
        is ParsingException.InvalidStatusCode -> code.toIntOrNull()
        else -> null
    }
    return statusCode == HttpURLConnection.HTTP_BAD_REQUEST ||
        statusCode == HttpURLConnection.HTTP_PAYMENT_REQUIRED ||
        statusCode == HttpURLConnection.HTTP_FORBIDDEN ||
        statusCode == 422
}

internal fun correctiveActionForConnectivity(
    isInternetConnected: Boolean,
    remainingWaypoints: List<Waypoint>
): CorrectiveAction =
    if (isInternetConnected) {
        CorrectiveAction.GetNewRoutes(remainingWaypoints)
    } else {
        CorrectiveAction.DoNothing
    }

fun RoutingMode.supportsTraffic(): Boolean = when (this) {
    RoutingMode.AUTO,
    RoutingMode.TRUCK,
    RoutingMode.BUS -> true
    else -> false
}

fun RoutingMode.valhallaCostingProfile(
    useTraffic: Boolean,
    routingOptions: RoutingOptions? = null
): ValhallaCostingProfile = when {
    useTraffic && this == RoutingMode.AUTO ->
        ValhallaCostingProfile.AutoTraffic.Premium
    useTraffic && this == RoutingMode.TRUCK ->
        ValhallaCostingProfile.TruckTraffic.Premium
    useTraffic && this == RoutingMode.BUS &&
        (routingOptions as? BusRoutingOptions)?.lineBus == true ->
        ValhallaCostingProfile.BusTraffic.Premium
    useTraffic && this == RoutingMode.BUS ->
        ValhallaCostingProfile.AutoTraffic.Premium
    this == RoutingMode.BUS && (routingOptions as? BusRoutingOptions)?.lineBus == true ->
        ValhallaCostingProfile.Bus
    this == RoutingMode.BUS -> ValhallaCostingProfile.Auto
    else -> ValhallaCostingProfile.fromRouteProviderProfile(value)
}

fun RoutingMode.valhallaProfile(
    useTraffic: Boolean,
    routingOptions: RoutingOptions? = null
): String = valhallaCostingProfile(useTraffic, routingOptions).routeProviderProfile
