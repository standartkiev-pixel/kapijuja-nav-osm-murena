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

package earth.maps.cardinal

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.rememberNavBackStack
import dagger.hilt.android.AndroidEntryPoint
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.FavoritesSyncMode
import earth.maps.cardinal.data.GeoIntentParser
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.ThemeMode
import earth.maps.cardinal.data.room.MigrationHelper
import earth.maps.cardinal.data.room.SavedListRepository
import earth.maps.cardinal.routing.FerrostarWrapperRepository
import earth.maps.cardinal.routing.RouteRepository
import earth.maps.cardinal.tileserver.LocalMapServerService
import earth.maps.cardinal.tileserver.PermissionRequest
import earth.maps.cardinal.tileserver.PermissionRequestManager
import earth.maps.cardinal.ui.core.AppContent
import earth.maps.cardinal.ui.core.CardinalNavigator
import earth.maps.cardinal.ui.core.CardinalRoute
import earth.maps.cardinal.ui.core.MapViewModel
import earth.maps.cardinal.ui.murena.MurenaFileSyncGateRoot
import earth.maps.cardinal.ui.settings.ThemeModePromptBottomSheet
import earth.maps.cardinal.ui.theme.AppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appPreferenceRepository: AppPreferenceRepository

    @Inject
    lateinit var ferrostarWrapperRepository: FerrostarWrapperRepository

    @Inject
    lateinit var permissionRequestManager: PermissionRequestManager

    @Inject
    lateinit var locationRepository: LocationRepository

    @Inject
    lateinit var routeRepository: RouteRepository

    @Inject
    lateinit var migrationHelper: MigrationHelper

    @Inject
    lateinit var savedListRepository: SavedListRepository

    private var localMapServerService: LocalMapServerService? = null
    private var bound by mutableStateOf(false)
    private var localMapServerBindingRequested = false
    private var port by mutableStateOf<Int?>(null)
    private var hasLocationPermission by mutableStateOf(false)
    private var hasNotificationPermission by mutableStateOf(false)
    private var deepLinkDestination by mutableStateOf<CardinalRoute?>(null)
    private var showLocationPermissionDialog by mutableStateOf(false)
    private var isLocationPermissionFlowActive by mutableStateOf(false)
    private var favoritesGateComplete by mutableStateOf(false)

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1002
        private const val TAG = "MainActivity"
        private const val GOOGLE_MAPS_REDIRECT_TIMEOUT_MS = 5_000
        private const val MAX_GOOGLE_MAPS_REDIRECTS = 5
        private const val HTTP_REDIRECT_STATUS_MIN = 300
        private const val HTTP_REDIRECT_STATUS_MAX = 399
        private const val KEY_FAVORITES_GATE_COMPLETE = "favorites_gate_complete"
        private const val MIN_LATITUDE = -90.0
        private const val MAX_LATITUDE = 90.0
        private const val MIN_LONGITUDE = -180.0
        private const val MAX_LONGITUDE = 180.0
        const val EXTRA_DEEP_LINK_DESTINATION = "deep_link_destination"
        const val DEEP_LINK_OFFLINE_AREAS = "offline_areas"
        private val GOOGLE_MAPS_HOSTS = setOf(
            "www.google.com",
            "maps.google.com",
            "maps.app.goo.gl",
            "goo.gl"
        )
        private val GOOGLE_MAPS_SHORT_LINK_HOSTS = setOf(
            "maps.app.goo.gl",
            "goo.gl"
        )
        private val GOOGLE_MAPS_DATA_COORDINATE_REGEX =
            Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
        private val AT_COORDINATE_REGEX =
            Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
        private val COORDINATE_PAIR_REGEX =
            Regex("""(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)""")
    }

    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun checkLocationPermission(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermission = true
        }
    }

    private fun requestLocationPermission() {
        isLocationPermissionFlowActive = true
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Permission request launcher for notification permission (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Log.d(TAG, "Notification permission granted")
            lifecycleScope.launch {
                permissionRequestManager.onPermissionGranted(PermissionRequest.NotificationPermission)
            }
        } else {
            Log.d(TAG, "Notification permission denied")
            lifecycleScope.launch {
                permissionRequestManager.onPermissionDenied(PermissionRequest.NotificationPermission)
            }
        }
    }

    // Permission request launcher for location permission (Android 13+)
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        isLocationPermissionFlowActive = false
        hasLocationPermission = isGranted
        if (isGranted) {
            locationRepository.startContinuousLocationUpdates(this@MainActivity)
            Log.d(TAG, "Location permission granted")
            // Request fresh location and animate camera to user's location
            lifecycleScope.launch {
                locationRepository.getCurrentLocation(this@MainActivity)?.let { location ->
                    Log.d(TAG, "Got location: ${location.latitude}, ${location.longitude}")
                    // The MapViewModel will handle the camera animation through its 
                    // handlePermissionStateChange method
                }
            }
        } else {
            Log.d(TAG, "Location permission denied")
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalMapServerService, cast the IBinder and get LocalMapServerService instance
            val binder = service as LocalMapServerService.LocalBinder
            localMapServerService = binder.getService()
            bound = true
            // Get the port
            port = localMapServerService?.getPort()
            Log.d(TAG, "Connected to tile server service on port: $port")

            // Configure Ferrostar to use the local routing endpoint
            port?.let { port ->
                val routingEndpoint = "http://127.0.0.1:$port/route"
                ferrostarWrapperRepository.setValhallaEndpoint(routingEndpoint)
                Log.d(TAG, "Configured Ferrostar to use local routing endpoint: $routingEndpoint")
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            bound = false
            localMapServerService = null
            port = null
            Log.d(TAG, "Disconnected from tile server service")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        initializeFavoritesGateState(savedInstanceState)
        initializePermissionState()
        launchStartupMaintenance()
        handleLaunchIntent(intent)
        setMainContent()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_FAVORITES_GATE_COMPLETE, favoritesGateComplete)
        super.onSaveInstanceState(outState)
    }

    private fun initializeFavoritesGateState(savedInstanceState: Bundle?) {
        val defaultGateState =
            appPreferenceRepository.favoritesSyncMode == FavoritesSyncMode.LOCAL_ONLY
        favoritesGateComplete = savedInstanceState?.getBoolean(
            KEY_FAVORITES_GATE_COMPLETE,
            defaultGateState
        ) ?: defaultGateState
    }

    private fun initializePermissionState() {
        hasNotificationPermission = checkNotificationPermission()
        hasLocationPermission = checkLocationPermission()
        startLocationUpdatesIfPermitted()
        maybeShowInitialLocationPermissionDialog()
    }

    private fun startLocationUpdatesIfPermitted() {
        if (hasLocationPermission) {
            locationRepository.startContinuousLocationUpdates(this@MainActivity)
        }
    }

    private fun maybeShowInitialLocationPermissionDialog() {
        if (!appPreferenceRepository.hasPromptedLocation.value && !hasLocationPermission) {
            showLocationPermissionDialog = true
            isLocationPermissionFlowActive = true
        }
    }

    private fun launchStartupMaintenance() {
        CoroutineScope(Dispatchers.IO).launch {
            migrationHelper.migratePlacesToSavedPlaces()
            savedListRepository.cleanupUnparentedElements()
        }

        intent?.takeIf { it.action == Intent.ACTION_VIEW }?.let { intent ->
            val data: Uri? = intent.data
            if (data != null && data.scheme != null) {
                when {
                    data.scheme.equals("geo") -> handleGeoIntent(data)
                    data.isSharedMapLinkUri() -> handleSharedMapLinkIntent(data)
                    data.isGoogleMapsUri() -> handleGoogleMapsIntent(data)
                }
            }
        }
    }

    private fun handleLaunchIntent(launchIntent: Intent?) {
        val viewIntent = launchIntent?.takeIf { it.action == Intent.ACTION_VIEW } ?: return
        viewIntent.data
            ?.takeIf { it.scheme == "geo" }
            ?.let(::handleGeoIntent)

        if (deepLinkDestination == null) {
            deepLinkDestination = routeForDeepLinkDestination(
                viewIntent.getStringExtra(EXTRA_DEEP_LINK_DESTINATION)
            )
        }
    }

    private fun setMainContent() {
        setContent {
            MainActivityContent()
        }
    }

    @Composable
    private fun MainActivityContent() {
        val contrastLevel by appPreferenceRepository.contrastLevel.collectAsState()
        val themeMode by appPreferenceRepository.themeMode.collectAsState()
        val hasPromptedThemeMode by appPreferenceRepository.hasPromptedThemeMode.collectAsState()
        val systemInDarkTheme = isSystemInDarkTheme()
        val darkTheme = themeMode.shouldUseDarkTheme(systemInDarkTheme)

        AppTheme(darkTheme = darkTheme, contrastLevel = contrastLevel) {
            val backStack = rememberNavBackStack(CardinalRoute.HomeSearch)
            val navigator = CardinalNavigator(backStack)
            NavigateToDeepLinkWhenGateCompletes(navigator)
            MainContentOrFileSyncGate(navigator, darkTheme)
            ThemePrompt(
                shouldShow = shouldShowThemePrompt(systemInDarkTheme, hasPromptedThemeMode),
                initialThemeMode = themeMode
            )
        }
    }

    @Composable
    private fun NavigateToDeepLinkWhenGateCompletes(navigator: CardinalNavigator) {
        LaunchedEffect(favoritesGateComplete, deepLinkDestination) {
            if (favoritesGateComplete) {
                deepLinkDestination?.let { route ->
                    Log.d(TAG, "Deep link: $route")
                    navigator.navigate(route, popUpToHome = true)
                    deepLinkDestination = null
                }
            }
        }
    }

    @Composable
    private fun MainContentOrFileSyncGate(
        navigator: CardinalNavigator,
        darkTheme: Boolean
    ) {
        if (favoritesGateComplete) {
            val mapViewModel: MapViewModel = hiltViewModel()

            AppContent(
                navigator = navigator,
                mapViewModel = mapViewModel,
                port = port,
                onRequestLocationPermission = { requestLocationPermission() },
                hasLocationPermission = hasLocationPermission,
                routeRepository = routeRepository,
                appPreferenceRepository = appPreferenceRepository,
                useDarkTheme = darkTheme,
                onRequestNotificationPermission = { requestNotificationPermission() },
                hasNotificationPermission = hasNotificationPermission,
                showLocationPermissionDialog = showLocationPermissionDialog,
                onDismissLocationDialog = ::dismissLocationPermissionDialog,
                onAcceptLocationDialog = ::acceptLocationPermissionDialog,
                onExitRequested = ::finish
            )
        } else {
            MurenaFileSyncGateRoot(
                onContinueToMap = {
                    favoritesGateComplete = true
                }
            )
        }
    }

    @Composable
    private fun ThemePrompt(shouldShow: Boolean, initialThemeMode: ThemeMode) {
        if (shouldShow) {
            ThemeModePromptBottomSheet(
                initialThemeMode = initialThemeMode,
                onDismiss = {
                    appPreferenceRepository.setHasPromptedThemeMode(true)
                },
                onSave = { selectedThemeMode ->
                    appPreferenceRepository.setThemeMode(selectedThemeMode)
                    appPreferenceRepository.setHasPromptedThemeMode(true)
                }
            )
        }
    }

    private fun shouldShowThemePrompt(
        systemInDarkTheme: Boolean,
        hasPromptedThemeMode: Boolean
    ): Boolean =
        favoritesGateComplete &&
            systemInDarkTheme &&
            !hasPromptedThemeMode &&
            !showLocationPermissionDialog &&
            !isLocationPermissionFlowActive

    private fun dismissLocationPermissionDialog() {
        showLocationPermissionDialog = false
        isLocationPermissionFlowActive = false
        appPreferenceRepository.setHasPromptedLocation(true)
    }

    private fun acceptLocationPermissionDialog() {
        showLocationPermissionDialog = false
        appPreferenceRepository.setHasPromptedLocation(true)
        requestLocationPermission()
    }

    private fun handleGeoIntent(data: Uri) {
        parseGeoIntent(data)?.let { place ->
            deepLinkDestination = routeForResolvedPlace(place)
        }
    }

    private fun handleSharedMapLinkIntent(data: Uri) {
        parseSharedMapLinkIntent(data)?.let { place ->
            navigateToPlace(place)
        }
    }

    private fun handleGoogleMapsIntent(data: Uri) {
        parseGoogleMapsIntent(data)?.let { place ->
            navigateToPlace(place)
            return
        }

        if (data.isGoogleMapsShortUri()) {
            lifecycleScope.launch {
                resolveRedirectUri(data)?.let { resolvedUri ->
                    parseGoogleMapsIntent(resolvedUri)?.let { place ->
                        navigateToPlace(place)
                    }
                }
            }
        }
    }

    private fun navigateToPlace(place: Place) {
        deepLinkDestination = routeForResolvedPlace(place)
    }

    private fun parseGeoIntent(data: Uri): Place? {
        return GeoIntentParser.parse(data)?.let { parsedGeoIntent ->
            locationRepository.fromNameAndLatLng(
                name = parsedGeoIntent.name,
                latLng = parsedGeoIntent.latLng
            )
        }
    }

    private fun parseSharedMapLinkIntent(data: Uri): Place? {
        val fragmentComponents = data.fragment
            ?.substringAfter("map=", missingDelimiterValue = "")
            ?.split('/')
            .orEmpty()
        val lat = data.getQueryParameter("lat")?.toDoubleOrNull()
            ?: data.getQueryParameter("mlat")?.toDoubleOrNull()
            ?: fragmentComponents.getOrNull(1)?.toDoubleOrNull()
        val lng = data.getQueryParameter("lng")?.toDoubleOrNull()
            ?: data.getQueryParameter("mlon")?.toDoubleOrNull()
            ?: fragmentComponents.getOrNull(2)?.toDoubleOrNull()
        if (lat != null && lng != null) {
            return locationRepository.fromNameAndLatLng(
                name = data.getQueryParameter("name"),
                latLng = LatLng(lat, lng)
            )
        }
        return null
    }

    private fun Uri.isSharedMapLinkUri(): Boolean {
        return scheme in setOf("http", "https") &&
            host == "share.maps.murena.com" &&
            path == "/shareplace"
    }

    private fun parseGoogleMapsIntent(data: Uri): Place? {
        val decodedUrl = Uri.decode(data.toString())
        val coordinates = extractGoogleMapsCoordinates(decodedUrl)
            ?: extractCoordinatePair(
                data.getQueryParameter("query")
                    ?: data.getQueryParameter("q")
                    ?: data.getQueryParameter("ll")
                    ?: data.getQueryParameter("center")
                    ?: data.getQueryParameter("destination")
                    ?: data.getQueryParameter("daddr")
            )
            ?: return null

        return locationRepository.fromNameAndLatLng(
            name = data.googleMapsPlaceName(),
            latLng = coordinates
        )
    }

    private fun extractGoogleMapsCoordinates(text: String): LatLng? {
        return GOOGLE_MAPS_DATA_COORDINATE_REGEX.find(text)?.toLatLng(
            latitudeGroup = 1,
            longitudeGroup = 2
        ) ?: AT_COORDINATE_REGEX.find(text)?.toLatLng(
            latitudeGroup = 1,
            longitudeGroup = 2
        ) ?: extractCoordinatePair(text)
    }

    private fun extractCoordinatePair(text: String?): LatLng? {
        if (text.isNullOrBlank()) return null
        return COORDINATE_PAIR_REGEX.find(text)?.toLatLng(
            latitudeGroup = 1,
            longitudeGroup = 2
        )
    }

    private fun MatchResult.toLatLng(latitudeGroup: Int, longitudeGroup: Int): LatLng? {
        val lat = groups[latitudeGroup]?.value?.toDoubleOrNull()
        val lng = groups[longitudeGroup]?.value?.toDoubleOrNull()
        if (
            lat != null &&
            lng != null &&
            lat in MIN_LATITUDE..MAX_LATITUDE &&
            lng in MIN_LONGITUDE..MAX_LONGITUDE
        ) {
            return LatLng(lat, lng)
        }
        return null
    }

    private fun Uri.googleMapsPlaceName(): String? {
        val placeName = pathSegments
            .dropWhile { it != "place" }
            .drop(1)
            .firstOrNull()
            ?.replace('+', ' ')
            ?.takeIf { !it.startsWith("@") }

        return placeName
            ?: getQueryParameter("query")?.takeUnless { extractCoordinatePair(it) != null }
            ?: getQueryParameter("q")?.takeUnless { extractCoordinatePair(it) != null }
    }

    private fun Uri.isGoogleMapsUri(): Boolean {
        return scheme in setOf("http", "https") &&
            host in GOOGLE_MAPS_HOSTS
    }

    private fun Uri.isGoogleMapsShortUri(): Boolean {
        return host in GOOGLE_MAPS_SHORT_LINK_HOSTS
    }

    private suspend fun resolveRedirectUri(data: Uri): Uri? = withContext(Dispatchers.IO) {
        var currentUrl = data.toString()
        repeat(MAX_GOOGLE_MAPS_REDIRECTS) {
            try {
                val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = GOOGLE_MAPS_REDIRECT_TIMEOUT_MS
                    readTimeout = GOOGLE_MAPS_REDIRECT_TIMEOUT_MS
                }
                val responseCode = connection.responseCode
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (
                    responseCode in HTTP_REDIRECT_STATUS_MIN..HTTP_REDIRECT_STATUS_MAX &&
                    !location.isNullOrBlank()
                ) {
                    currentUrl = URL(URL(currentUrl), location).toString()
                } else {
                    return@withContext Uri.parse(currentUrl)
                }
            } catch (exception: Exception) {
                Log.w(TAG, "Failed to resolve Google Maps short link", exception)
                return@withContext null
            }
        }
        Uri.parse(currentUrl)
    }

    override fun onStart() {
        super.onStart()

        // Observe permission requests from services
        lifecycleScope.launch {
            permissionRequestManager.permissionRequests.collect { request ->
                when (request) {
                    is PermissionRequest.NotificationPermission -> {
                        Log.d(TAG, "Handling notification permission request")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // Permission not required for older Android versions
                            permissionRequestManager.onPermissionGranted(request)
                        }
                    }
                }
            }
        }

        bindLocalMapServerService()
    }

    override fun onResume() {
        super.onResume()
        startLocalMapServerService()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (localMapServerBindingRequested) {
            unbindService(connection)
            localMapServerBindingRequested = false
            bound = false
            localMapServerService = null
            port = null
        }
    }

    private fun bindLocalMapServerService() {
        if (localMapServerBindingRequested) return

        val serviceIntent = Intent(this, LocalMapServerService::class.java)
        localMapServerBindingRequested = bindService(serviceIntent, connection, BIND_AUTO_CREATE)
        if (!localMapServerBindingRequested) {
            Log.w(TAG, "Unable to bind local map server service")
        }
    }

    private fun startLocalMapServerService() {
        try {
            startService(Intent(this, LocalMapServerService::class.java))
        } catch (exception: IllegalStateException) {
            Log.w(
                TAG,
                "Unable to start local map server service; continuing with bound service lifecycle",
                exception
            )
        }
    }
}

internal fun routeForDeepLinkDestination(destination: String?): CardinalRoute? =
    when (destination) {
        MainActivity.DEEP_LINK_OFFLINE_AREAS -> CardinalRoute.OfflineAreas
        else -> null
    }

internal fun routeForResolvedPlace(place: Place): CardinalRoute.PlaceCard =
    CardinalRoute.PlaceCard(place)
