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

package earth.maps.cardinal.ui.core

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.times
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import earth.maps.cardinal.R.color
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.R.drawable
import earth.maps.cardinal.R.string
import earth.maps.cardinal.bottomsheet.BottomSheetScaffold
import earth.maps.cardinal.bottomsheet.BottomSheetScaffoldState
import earth.maps.cardinal.bottomsheet.BottomSheetValue
import earth.maps.cardinal.bottomsheet.rememberBottomSheetScaffoldState
import earth.maps.cardinal.bottomsheet.rememberBottomSheetState
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.PolylineUtils
import earth.maps.cardinal.routing.RouteRepository
import earth.maps.cardinal.ui.directions.DirectionsScreen
import earth.maps.cardinal.ui.directions.DirectionsViewModel
import earth.maps.cardinal.ui.home.HomeScreen
import earth.maps.cardinal.ui.home.HomeViewModel
import earth.maps.cardinal.ui.home.NearbyCategoryFilterScreen
import earth.maps.cardinal.ui.home.NearbyScreenContent
import earth.maps.cardinal.ui.home.NearbyViewModel
import earth.maps.cardinal.ui.home.OfflineAreasScreen
import earth.maps.cardinal.ui.home.OfflineAreasViewModel
import earth.maps.cardinal.ui.home.TransitScreenContent
import earth.maps.cardinal.ui.home.TransitScreenViewModel
import earth.maps.cardinal.ui.navigation.TurnByTurnNavigationScreen
import earth.maps.cardinal.ui.place.PlaceCardScreen
import earth.maps.cardinal.ui.place.PlaceCardViewModel
import earth.maps.cardinal.ui.saved.ManagePlacesScreen
import earth.maps.cardinal.ui.settings.AccessibilitySettingsScreen
import earth.maps.cardinal.ui.settings.AdvancedSettingsScreen
import earth.maps.cardinal.ui.settings.PrivacySettingsScreen
import earth.maps.cardinal.ui.settings.ProfileEditorScreen
import earth.maps.cardinal.ui.settings.RoutingProfilesScreen
import earth.maps.cardinal.ui.settings.SettingsScreen
import earth.maps.cardinal.ui.settings.SettingsViewModel
import earth.maps.cardinal.ui.settings.ThemeSettingsScreen
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.util.VisibleRegion
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import earth.maps.cardinal.data.BoundingBox as CardinalBoundingBox

val TOOLBAR_HEIGHT_DP = 64.dp
private const val PLACE_CARD_DEFAULT_ZOOM = 15.0
private const val SEARCH_RESULTS_MIN_ZOOM_ON_SUBMIT = 14.0
private const val SEARCH_RESULTS_TO_FIT_WHEN_CAPPED_VIEW_IS_EMPTY = 3

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(
    navigator: CardinalNavigator,
    mapViewModel: MapViewModel,
    port: Int?,
    onRequestLocationPermission: () -> Unit,
    hasLocationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    hasNotificationPermission: Boolean,
    routeRepository: RouteRepository,
    appPreferenceRepository: AppPreferenceRepository,
    useDarkTheme: Boolean,
    showLocationPermissionDialog: Boolean = false,
    onDismissLocationDialog: () -> Unit = {},
    onAcceptLocationDialog: () -> Unit = {},
    onExitRequested: () -> Unit = {},
    state: AppContentState = rememberAppContentState(),
) {

    val homeViewModel: HomeViewModel = hiltViewModel()
    val transitViewModel: TransitScreenViewModel = hiltViewModel()
    val nearbyViewModel: NearbyViewModel = hiltViewModel()

    val droppedPinName = stringResource(string.dropped_pin)

    // This is used by nav destinations to determine if it is appropriate of them to update peekHeight.
    val topOfBackStack = navigator.currentRoute
    val showToolbar = shouldShowToolbar(topOfBackStack)
    val transitOverlayCoordinator = remember(state) {
        TransitMapOverlayCoordinator(AppContentTransitMapOverlaySink(state))
    }

    ClearTransitMapStateOutsideTransitRoutes(
        coordinator = transitOverlayCoordinator,
        topOfBackStack = topOfBackStack
    )

    // See comment below in onGloballyPositioned for why this is necessary. I'm not happy about it either.
    LaunchedEffect(state.peekHeight) {
        mapViewModel.peekHeight = state.peekHeight
    }
    MapViewContainer(
        port = port,
        mapViewModel = mapViewModel,
        state = state,
        navigator = navigator,
        topOfBackStack = topOfBackStack,
        droppedPinName = droppedPinName,
        onRequestLocationPermission = onRequestLocationPermission,
        hasLocationPermission = hasLocationPermission,
        appPreferenceRepository = appPreferenceRepository,
        useDarkTheme = useDarkTheme
    )

    val appEntryProvider = entryProvider<NavKey> {
        entry<CardinalRoute.HomeSearch>(metadata = verticalTransitionMetadata()) { route ->
            HomeRoute(
                state,
                homeViewModel,
                navigator,
                topOfBackStack,
                appPreferenceRepository,
                route
            )
        }

        entry<CardinalRoute.NearbyPoi>(metadata = verticalTransitionMetadata()) { route ->
            NearbyPoiRoute(state, nearbyViewModel, navigator, topOfBackStack, route)
        }

        entry<CardinalRoute.NearbyCategoryFilters>(metadata = horizontalTransitionMetadata()) {
            NearbyCategoryFiltersRoute(state, nearbyViewModel, navigator)
        }

        entry<CardinalRoute.NearbyTransit>(metadata = verticalTransitionMetadata()) { route ->
            NearbyTransitRoute(
                state,
                transitViewModel,
                navigator,
                topOfBackStack,
                route
            )
        }

        entry<CardinalRoute.PlaceCard>(metadata = verticalTransitionMetadata()) { route ->
            PlaceCardRoute(
                state,
                navigator,
                topOfBackStack,
                appPreferenceRepository,
                route
            )
        }

        entry<CardinalRoute.OfflineAreas>(metadata = verticalTransitionMetadata()) { route ->
            OfflineAreasRoute(
                state,
                navigator,
                topOfBackStack,
                appPreferenceRepository,
                route
            )
        }

        entry<CardinalRoute.Settings>(metadata = horizontalTransitionMetadata()) {
            SettingsRoute(state, navigator)
        }

        entry<CardinalRoute.OfflineSettings>(metadata = horizontalTransitionMetadata()) {
            PrivacySettingsRoute(state, navigator)
        }

        entry<CardinalRoute.AccessibilitySettings>(metadata = horizontalTransitionMetadata()) {
            AccessibilitySettingsRoute(state, navigator)
        }

        entry<CardinalRoute.ThemeSettings>(metadata = horizontalTransitionMetadata()) {
            ThemeSettingsRoute(state, navigator)
        }

        entry<CardinalRoute.AdvancedSettings>(metadata = horizontalTransitionMetadata()) {
            AdvancedSettingsRoute(state, navigator)
        }

        entry<CardinalRoute.RoutingProfiles>(metadata = horizontalTransitionMetadata()) {
            RoutingProfilesRoute(state, navigator)
        }

        entry<CardinalRoute.ProfileEditor>(metadata = horizontalTransitionMetadata()) { route ->
            ProfileEditorRoute(state, navigator, route)
        }

        entry<CardinalRoute.ManagePlaces>(metadata = horizontalTransitionMetadata()) { route ->
            ManagePlacesRoute(state, navigator, route)
        }

        entry<CardinalRoute.Directions>(metadata = verticalTransitionMetadata()) { route ->
            DirectionsRoute(
                state,
                mapViewModel,
                navigator,
                topOfBackStack,
                appPreferenceRepository,
                hasLocationPermission,
                onRequestLocationPermission,
                hasNotificationPermission,
                onRequestNotificationPermission,
                transitOverlayCoordinator,
                route
            )
        }

        entry<CardinalRoute.TransitItineraryDetail>(metadata = verticalTransitionMetadata()) { route ->
            TransitItineraryDetailRoute(
                state,
                navigator,
                topOfBackStack,
                appPreferenceRepository,
                transitOverlayCoordinator,
                route
            )
        }

        entry<CardinalRoute.TurnByTurnNavigation> { route ->
            TurnByTurnRoute(state, routeRepository, useDarkTheme, port, route)
        }
    }

    NavDisplay(
        backStack = navigator.backStack,
        onBack = {
            if (!navigator.goBack()) {
                onExitRequested()
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = appEntryProvider,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated toolbar positioned below the scaffold
        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = showToolbar,
            enter = slideInVertically(
                initialOffsetY = { it }, animationSpec = tween(300)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it }, animationSpec = tween(300)
            ),
        ) {
            CardinalToolbar(navigator, onSearchDoublePress = { homeViewModel.expandSearch() })
        }
    }

    // Show location permission dialog on first startup
    if (showLocationPermissionDialog) {
        LocationPermissionDialog(
            onDismiss = onDismissLocationDialog,
            onAccept = {
                // Mark that we have a pending location request so the camera will animate
                // when permission is granted
                mapViewModel.markLocationRequestPending()
                onAcceptLocationDialog()
            }
        )
    }
}

private fun verticalTransitionMetadata(): Map<String, Any> =
    NavDisplay.transitionSpec {
        slideInVertically(initialOffsetY = { it }) togetherWith
            fadeOut(animationSpec = tween(600))
    }

private fun horizontalTransitionMetadata(): Map<String, Any> =
    NavDisplay.transitionSpec {
        slideInHorizontally(initialOffsetX = { it }) togetherWith
            slideOutHorizontally(targetOffsetX = { -it })
    } + NavDisplay.popTransitionSpec {
        slideInHorizontally(initialOffsetX = { -it }) togetherWith
            slideOutHorizontally(targetOffsetX = { it })
    } + NavDisplay.predictivePopTransitionSpec {
        slideInHorizontally(initialOffsetX = { -it }) togetherWith
            slideOutHorizontally(targetOffsetX = { it })
    }

private fun shouldShowToolbar(route: CardinalRoute): Boolean = when (route) {
    CardinalRoute.HomeSearch,
    CardinalRoute.NearbyPoi,
    CardinalRoute.NearbyTransit,
    is CardinalRoute.ManagePlaces,
    CardinalRoute.OfflineAreas,
    CardinalRoute.Settings,
    CardinalRoute.OfflineSettings,
    CardinalRoute.AccessibilitySettings,
    CardinalRoute.ThemeSettings,
    CardinalRoute.AdvancedSettings,
    CardinalRoute.RoutingProfiles,
    is CardinalRoute.ProfileEditor -> true

    is CardinalRoute.PlaceCard,
    is CardinalRoute.Directions,
    is CardinalRoute.TransitItineraryDetail,
    CardinalRoute.NearbyCategoryFilters,
    is CardinalRoute.TurnByTurnNavigation -> false
}

@Composable
@Suppress("CognitiveComplexMethod")
private fun MapViewContainer(
    port: Int?,
    mapViewModel: MapViewModel,
    state: AppContentState,
    navigator: CardinalNavigator,
    topOfBackStack: CardinalRoute,
    droppedPinName: String,
    onRequestLocationPermission: () -> Unit,
    hasLocationPermission: Boolean,
    appPreferenceRepository: AppPreferenceRepository,
    useDarkTheme: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                state.screenHeightDp = with(state.density) { it.size.height.toDp() }
                state.screenWidthDp = with(state.density) { it.size.width.toDp() }
                // For very annoying reasons, this ViewModel needs to know the size of the screen.
                // Specifically, it is responsible for tracking the state of the "locate me" button across
                // a permission request lifecycle. When the permission request is done, it has zero
                // business calling back into the view to perform the animateTo operation, and in order
                // to perform the animateTo you need to calculate padding based on screen size and peek
                // height. :(
                mapViewModel.screenWidth = state.screenWidthDp
                mapViewModel.screenHeight = state.screenHeightDp
            },
    ) {
        if (port != null && port != -1) {
            MapView(
                port = port,
                mapViewModel = mapViewModel,
                onMapInteraction = {
                    if (topOfBackStack is CardinalRoute.PlaceCard) {
                        navigator.goBack()
                    }
                },
                onMapPoiClick = {
                    if (
                        topOfBackStack !is CardinalRoute.Directions &&
                        topOfBackStack !is CardinalRoute.TransitItineraryDetail
                    ) {
                        state.coroutineScope.launch {
                            navigator.navigate(
                                CardinalRoute.PlaceCard(
                                    place = mapViewModel.enrichPlaceWithReverseGeocodedCountry(it),
                                    preserveMapZoom = true
                                )
                            )
                        }
                    }
                },
                onDropPin = {
                    val place = Place(
                        name = droppedPinName,
                        description = "",
                        icon = "place",
                        latLng = it,
                        address = null,
                        isMyLocation = false
                    )
                    state.coroutineScope.launch {
                        navigator.navigate(
                            CardinalRoute.PlaceCard(
                                place = mapViewModel.enrichPlaceWithReverseGeocodedCountry(place),
                                preserveMapZoom = true
                            )
                        )
                    }
                },
                onRequestLocationPermission = onRequestLocationPermission,
                hasLocationPermission = hasLocationPermission,
                fabInsets = PaddingValues(
                    start = 0.dp,
                    top = 0.dp,
                    end = 0.dp,
                    bottom = if (state.screenHeightDp > state.fabHeight) {
                        state.screenHeightDp - state.fabHeight
                    } else {
                        0.dp
                    }
                ),
                cameraState = state.cameraState,
                screenWidthDp = state.screenWidthDp,
                screenHeightDp = state.screenHeightDp,
                mapPins = state.mapPins,
                appPreferences = appPreferenceRepository,
                useDarkTheme = useDarkTheme,
                selectedOfflineArea = state.selectedOfflineArea,
                currentRoute = state.currentRoute,
                allRoutes = state.allRoutes,
                trafficAvailable = state.trafficAvailable,
                etaCorrectionFactor = state.etaCorrectionFactor,
                currentTransitItinerary = state.currentTransitItinerary,
                highlightedTransitLegIndex = state.highlightedTransitLegIndex,
                onRouteAnnotationClick = { routeIndex ->
                    handleRouteAnnotationClick(routeIndex, state)
                }
            )
        } else {
            LaunchedEffect(key1 = port) {
                Log.e("AppContent", "Tileserver port is $port, can't display a map!")
            }
        }

        Box(
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            BirdSettingsFab(navigator)
        }
    }
}

private fun handleRouteAnnotationClick(routeIndex: Int, state: AppContentState) {
    // Handle route annotation click by updating the selected route index in AppContentState
    // The DirectionsScreen will observe this change and update the DirectionsViewModel
    if (state.allRoutes.isNotEmpty()) {
        val actualIndex = if (routeIndex == -1) {
            // If -1 is passed, it means the current selected route was tapped
            // Keep the current selection
            state.selectedRouteIndex ?: 0
        } else {
            // Convert the reversed index back to the correct index
            // because routes are displayed in reverse order in the RouteLayer
            state.allRoutes.size - 1 - routeIndex
        }

        if (actualIndex >= 0 && actualIndex < state.allRoutes.size) {
            state.selectedRouteIndex = actualIndex
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRoute(
    state: AppContentState,
    homeViewModel: HomeViewModel,
    navigator: CardinalNavigator,
    topOfBackStack: CardinalRoute,
    appPreferenceRepository: AppPreferenceRepository,
    route: CardinalRoute.HomeSearch
) {
    LaunchedEffect(Unit) {
        state.mapPins.clear()
        state.currentRoute = null
        state.allRoutes = emptyList()
    }
    state.showToolbar = true
    HomeScreenComposable(
        viewModel = homeViewModel,
        cameraState = state.cameraState,
        mapPins = state.mapPins,
        peekHeight = state.peekHeight,
        navigator = navigator,
        onPeekHeightChange = {
            state.peekHeight = it
        },
        onFabHeightChange = {
            state.fabHeight = it
        },
        topOfBackStack = topOfBackStack,
        route = route,
        screenWidthDp = state.screenWidthDp,
        screenHeightDp = state.screenHeightDp,
        appPreferenceRepository = appPreferenceRepository
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbyPoiRoute(
    state: AppContentState,
    nearbyViewModel: NearbyViewModel,
    navigator: CardinalNavigator,
    topOfBackStack: CardinalRoute,
    route: CardinalRoute.NearbyPoi
) {
    state.showToolbar = true
    val bottomSheetState = rememberBottomSheetState(
        initialValue = BottomSheetValue.Collapsed
    )
    val scaffoldState =
        rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    CardinalAppScaffold(
        scaffoldState = scaffoldState,
        peekHeight = state.screenHeightDp / 3,
        showToolbar = true,
        content = {
            NearbyScreenContent(
                viewModel = nearbyViewModel,
                onPlaceSelected = {
                    navigator.navigate(CardinalRoute.PlaceCard(it))
                },
                onFilterClick = {
                    navigator.navigate(CardinalRoute.NearbyCategoryFilters)
                }
            )
        },
        fabHeightCallback = {
            if (topOfBackStack == route) {
                state.fabHeight = it
            }
        },
    )
}

@Composable
private fun NearbyCategoryFiltersRoute(
    state: AppContentState,
    nearbyViewModel: NearbyViewModel,
    navigator: CardinalNavigator
) {
    state.showToolbar = false
    NearbyCategoryFilterScreen(
        viewModel = nearbyViewModel,
        onBack = { navigator.goBack() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NearbyTransitRoute(
    state: AppContentState,
    transitViewModel: TransitScreenViewModel,
    navigator: CardinalNavigator,
    topOfBackStack: CardinalRoute,
    route: CardinalRoute.NearbyTransit
) {
    state.showToolbar = true

    val bottomSheetState = rememberBottomSheetState(
        initialValue = BottomSheetValue.Collapsed
    )
    val scaffoldState =
        rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    CardinalAppScaffold(
        scaffoldState = scaffoldState,
        peekHeight = state.screenHeightDp / 3,
        showToolbar = true,
        content = {
            TransitScreenContent(viewModel = transitViewModel, onRouteClicked = {
                navigator.navigate(CardinalRoute.PlaceCard(it))
            })
        },
        fabHeightCallback = {
            if (topOfBackStack == route) {
                state.fabHeight = it
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRoute(state: AppContentState, navigator: CardinalNavigator) {
    state.showToolbar = true
    val viewModel = hiltViewModel<SettingsViewModel>()
    SettingsScreen(navigator = navigator, viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySettingsRoute(state: AppContentState, navigator: CardinalNavigator) {
    state.showToolbar = true
    val viewModel: SettingsViewModel = hiltViewModel()
    PrivacySettingsScreen(
        viewModel = viewModel,
        onDismiss = { navigator.goBack() },
        onNavigateToOfflineAreas = {
            navigator.navigate(CardinalRoute.OfflineAreas)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccessibilitySettingsRoute(state: AppContentState, navigator: CardinalNavigator) {
    state.showToolbar = true
    val viewModel: SettingsViewModel = hiltViewModel()
    AccessibilitySettingsScreen(viewModel = viewModel, onDismiss = { navigator.goBack() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingsRoute(state: AppContentState, navigator: CardinalNavigator) {
    state.showToolbar = true
    ThemeSettingsScreen(onDismiss = { navigator.goBack() })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedSettingsRoute(state: AppContentState, navigator: CardinalNavigator) {
    state.showToolbar = true
    val viewModel: SettingsViewModel = hiltViewModel()
    AdvancedSettingsScreen(viewModel = viewModel)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutingProfilesRoute(state: AppContentState, navigator: CardinalNavigator) {
    state.showToolbar = true
    RoutingProfilesScreen(navigator = navigator)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditorRoute(
    state: AppContentState,
    navigator: CardinalNavigator,
    route: CardinalRoute.ProfileEditor
) {
    LaunchedEffect(key1 = Unit) {
        state.mapPins.clear()
        state.currentRoute = null
        state.allRoutes = emptyList()
    }

    val snackBarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackBarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing,
        content = { padding ->
            Box(modifier = Modifier.padding(padding)) {
                ProfileEditorScreen(
                    onBack = { navigator.goBack() },
                    profileId = route.profileId,
                    snackBarHostState = snackBarHostState
                )
            }
        })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManagePlacesRoute(
    state: AppContentState,
    navigator: CardinalNavigator,
    route: CardinalRoute.ManagePlaces
) {
    state.showToolbar = true

    ManagePlacesScreen(
        navigator = navigator,
        listId = route.listId,
        parents = route.parents,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceCardRoute(
    state: AppContentState,
    navigator: CardinalNavigator,
    topOfBackStack: CardinalRoute,
    appPreferenceRepository: AppPreferenceRepository,
    route: CardinalRoute.PlaceCard
) {
    state.showToolbar = false

    val bottomSheetState = rememberBottomSheetState(
        initialValue = BottomSheetValue.Collapsed
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    LaunchedEffect(key1 = Unit) {
        // The place card starts partially expanded.
        state.coroutineScope.launch {
            scaffoldState.bottomSheetState.collapse()
        }
    }

    val viewModel: PlaceCardViewModel = hiltViewModel()
    val place = route.place ?: return
    LaunchedEffect(place) {
        updatePlaceCardMapState(
            state = state,
            navigator = navigator,
            appPreferenceRepository = appPreferenceRepository,
            viewModel = viewModel,
            place = place,
            preserveMapZoom = route.preserveMapZoom,
        )
    }

    PlaceCardScaffold(
        state = state,
        navigator = navigator,
        topOfBackStack = topOfBackStack,
        appPreferenceRepository = appPreferenceRepository,
        scaffoldState = scaffoldState,
        viewModel = viewModel,
        place = place,
        route = route,
    )
}

private suspend fun updatePlaceCardMapState(
    state: AppContentState,
    navigator: CardinalNavigator,
    appPreferenceRepository: AppPreferenceRepository,
    viewModel: PlaceCardViewModel,
    place: Place,
    preserveMapZoom: Boolean,
) {
    viewModel.setPlace(place)
    // Clear any existing pins and add the new one to ensure only one pin is shown at a time
    state.mapPins.clear()
    state.currentRoute = null
    state.allRoutes = emptyList()
    state.mapPins.add(place)

    val shouldFlyToPoi = navigator.previousRoute !is CardinalRoute.Directions

    // Only animate if we're entering from the home screen, as opposed to e.g. popping from the
    // settings screen. This is brittle and may break if we end up with more entry points.
    if (shouldFlyToPoi) {
        state.coroutineScope.launch {
            state.cameraState.animateTo(
                CameraPosition(
                    target = Position(
                        latitude = place.latLng.latitude,
                        longitude = place.latLng.longitude
                    ),
                    zoom = if (preserveMapZoom) {
                        state.cameraState.position.zoom
                    } else {
                        PLACE_CARD_DEFAULT_ZOOM
                    },
                    padding = PaddingValues(
                        start = state.screenWidthDp / 8,
                        top = state.screenHeightDp / 8,
                        end = state.screenWidthDp / 8,
                        bottom = min(
                            3f * state.screenHeightDp / 4,
                            state.peekHeight + state.screenHeightDp / 8
                        )
                    )
                ),
                duration = appPreferenceRepository.animationSpeedDurationValue,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceCardScaffold(
    state: AppContentState,
    navigator: CardinalNavigator,
    topOfBackStack: CardinalRoute,
    appPreferenceRepository: AppPreferenceRepository,
    scaffoldState: BottomSheetScaffoldState,
    viewModel: PlaceCardViewModel,
    place: Place,
    route: CardinalRoute.PlaceCard,
) {
    CardinalAppScaffold(
        scaffoldState = scaffoldState, peekHeight = state.peekHeight,
        content = {
            PlaceCardScreen(place = place, viewModel = viewModel, onBack = {
                navigator.goBack()
            }, onGetDirections = { place ->
                navigator.navigate(CardinalRoute.Directions(fromPlace = null, toPlace = place))
            }, appPreferences = appPreferenceRepository, onPeekHeightChange = {
                updatePlaceCardPeekHeight(state, topOfBackStack, route, it)
            })
        },
        showToolbar = false,
        fabHeightCallback = {
            updatePlaceCardFabHeight(state, topOfBackStack, route, it)
        },
    )
}

private fun updatePlaceCardPeekHeight(
    state: AppContentState,
    topOfBackStack: CardinalRoute,
    route: CardinalRoute.PlaceCard,
    peekHeight: Dp,
) {
    if (topOfBackStack == route) {
        state.peekHeight = peekHeight
    }
}

private fun updatePlaceCardFabHeight(
    state: AppContentState,
    topOfBackStack: CardinalRoute,
    route: CardinalRoute.PlaceCard,
    fabHeight: Dp,
) {
    if (topOfBackStack == route) {
        state.fabHeight = fabHeight
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfflineAreasRoute(
    state: AppContentState,
    navigator: CardinalNavigator,
    topOfBackStack: CardinalRoute,
    appPreferenceRepository: AppPreferenceRepository,
    route: CardinalRoute.OfflineAreas
) {
    state.showToolbar = true
    val bottomSheetState = rememberBottomSheetState(
        initialValue = BottomSheetValue.Collapsed
    )
    val scaffoldState =
        rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    LaunchedEffect(key1 = Unit) {
        state.currentRoute = null
        state.allRoutes = emptyList()
        state.mapPins.clear()
        state.peekHeight = state.screenHeightDp / 3 // Approx, empirical
        state.coroutineScope.launch {
            scaffoldState.bottomSheetState.collapse()
        }
    }
    DisposableEffect(key1 = Unit) {
        onDispose {
            state.selectedOfflineArea = null
        }
    }
    val viewModel: OfflineAreasViewModel = hiltViewModel()

    // Track the current viewport reactively
    var currentViewport by remember { mutableStateOf(state.cameraState.projection?.queryVisibleRegion()) }

    // Update viewport when camera state changes
    LaunchedEffect(state.cameraState.position) {
        currentViewport = state.cameraState.projection?.queryVisibleRegion()
    }

    currentViewport?.let { visibleRegion ->
        CardinalAppScaffold(
            scaffoldState = scaffoldState,
            peekHeight = state.peekHeight,
            fabHeightCallback = {
                if (topOfBackStack == route) {
                    state.fabHeight = it
                }
            },
            showToolbar = true,
            content = { snackBarHostState ->
                OfflineAreasScreen(
                    currentViewport = visibleRegion,
                    currentZoom = state.cameraState.position.zoom,
                    viewModel = viewModel,
                    snackBarHostState = snackBarHostState,
                    onDismiss = {
                        navigator.goBack()
                    },
                    onAreaSelected = { area ->
                        state.coroutineScope.launch {
                            scaffoldState.bottomSheetState.collapse()
                            state.cameraState.animateTo(
                                boundingBox = BoundingBox(
                                    area.west, area.south, area.east, area.north
                                ),
                                padding = PaddingValues(
                                    start = state.screenWidthDp / 8,
                                    top = state.screenHeightDp / 8,
                                    end = state.screenWidthDp / 8,
                                    bottom = min(
                                        3f * state.screenHeightDp / 4,
                                        state.peekHeight + state.screenHeightDp / 8
                                    )
                                ),
                                duration = appPreferenceRepository.animationSpeedDurationValue
                            )
                        }
                        state.selectedOfflineArea = area
                    })
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectionsRoute(
    state: AppContentState,
    mapViewModel: MapViewModel,
    navigator: CardinalNavigator,
    topOfBackStack: CardinalRoute,
    appPreferenceRepository: AppPreferenceRepository,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    transitOverlayCoordinator: TransitMapOverlayCoordinator,
    route: CardinalRoute.Directions
) {
    state.showToolbar = false
    val bottomSheetState =
        rememberBottomSheetState(initialValue = BottomSheetValue.Collapsed)
    val scaffoldState =
        rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    val viewModel: DirectionsViewModel = hiltViewModel()
    mapViewModel.locationFlow.collectAsState().value

    val polylinePadding = PaddingValues(
        start = state.screenWidthDp / 8,
        top = state.screenHeightDp / 8,
        end = state.screenWidthDp / 8,
        bottom = min(
            3f * state.screenHeightDp / 4,
            state.peekHeight + state.screenHeightDp / 8
        )
    )

    DirectionsRouteMapOrchestrator(
        state = state,
        route = route,
        topOfBackStack = topOfBackStack,
        viewModel = viewModel,
        appPreferences = appPreferenceRepository,
        transitOverlayCoordinator = transitOverlayCoordinator,
        polylinePadding = polylinePadding
    )

    CardinalAppScaffold(
        scaffoldState = scaffoldState, peekHeight = state.peekHeight,
        content = {
            DirectionsScreen(
                viewModel = viewModel,
                onPeekHeightChange = {
                    if (topOfBackStack == route) {
                        state.peekHeight = it
                    }
                },
                onBack = { navigator.goBack() },
                onFullExpansionRequired = {
                    state.coroutineScope.launch {
                        scaffoldState.bottomSheetState.expand()
                    }
                },
                navigator = navigator,
                hasLocationPermission = hasLocationPermission,
                onRequestLocationPermission = onRequestLocationPermission,
                hasNotificationPermission = hasNotificationPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
                appPreferences = appPreferenceRepository
            )
        },
        showToolbar = false,
        fabHeightCallback = {
            if (topOfBackStack == route) {
                state.fabHeight = it
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransitItineraryDetailRoute(
    state: AppContentState,
    navigator: CardinalNavigator,
    topOfBackStack: CardinalRoute,
    appPreferenceRepository: AppPreferenceRepository,
    transitOverlayCoordinator: TransitMapOverlayCoordinator,
    route: CardinalRoute.TransitItineraryDetail
) {
    state.showToolbar = false

    val bottomSheetState = rememberBottomSheetState(
        initialValue = BottomSheetValue.Collapsed
    )
    val scaffoldState =
        rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    val itinerary = route.itinerary
    val onTransitLegClick = rememberTransitItineraryDetailMapOrchestrator(
        state = state,
        itinerary = itinerary,
        appPreferences = appPreferenceRepository,
        transitOverlayCoordinator = transitOverlayCoordinator,
        scaffoldState = scaffoldState
    )

    itinerary?.let { itinerary ->
        CardinalAppScaffold(
            scaffoldState = scaffoldState,
            peekHeight = state.peekHeight,
            content = {
                earth.maps.cardinal.ui.directions.TransitItineraryDetailScreen(
                    itinerary = itinerary, onBack = {
                        navigator.goBack()
                    }, onLegClick = onTransitLegClick, appPreferences = appPreferenceRepository
                )
            },
            showToolbar = false,
            fabHeightCallback = {
                if (topOfBackStack == route) {
                    state.fabHeight = it
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TurnByTurnRoute(
    state: AppContentState,
    routeRepository: RouteRepository,
    useDarkTheme: Boolean,
    port: Int?,
    route: CardinalRoute.TurnByTurnNavigation
) {
    state.showToolbar = false

    val ferrostarRoute = route.routeId.let {
        try {
            routeRepository.getRoute(it)
        } catch (_: Exception) {
            null
        }
    }

    port?.let { port ->
        TurnByTurnNavigationScreen(
            port = port,
            mode = route.routingMode,
            route = ferrostarRoute,
            useDarkTheme = useDarkTheme,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CardinalToolbar(
    navigator: CardinalNavigator, onSearchDoublePress: (() -> Unit)? = null
) {
    FlexibleBottomAppBar {
        IconButton(onClick = {
            navigator.navigate(CardinalRoute.ManagePlaces(null), popUpToHome = true)
        }) {
            Icon(
                painter = painterResource(drawable.ic_star), contentDescription = stringResource(
                    string.favorites_screen
                )
            )
        }
        IconButton(onClick = {
            navigator.navigate(CardinalRoute.NearbyPoi, popUpToHome = true)
        }) {
            Icon(
                painter = painterResource(drawable.ic_nearby),
                contentDescription = stringResource(string.points_of_interest_nearby)
            )
        }
        FilledIconButton(onClick = {
            if (navigator.currentRoute == CardinalRoute.HomeSearch) {
                onSearchDoublePress?.invoke()
            } else {
                navigator.navigate(CardinalRoute.HomeSearch, popUpToHome = true)
            }
        }) {
            Icon(
                painter = painterResource(drawable.ic_search),
                contentDescription = stringResource(string.search)
            )
        }
        IconButton(onClick = {
            navigator.navigate(CardinalRoute.NearbyTransit, popUpToHome = true)
        }) {
            Icon(
                painter = painterResource(drawable.ic_bus_railway),
                contentDescription = stringResource(
                    string.public_transportation_nearby
                )
            )
        }
        IconButton(onClick = {
            navigator.navigate(CardinalRoute.OfflineAreas, popUpToHome = true)
        }) {
            Icon(
                painter = painterResource(drawable.cloud_download_24dp),
                contentDescription = stringResource(string.directions)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeScreenComposable(
    viewModel: HomeViewModel,
    cameraState: CameraState,
    mapPins: SnapshotStateList<Place>,
    peekHeight: Dp,
    navigator: CardinalNavigator,
    onPeekHeightChange: (Dp) -> Unit,
    onFabHeightChange: (Dp) -> Unit,
    topOfBackStack: CardinalRoute,
    route: CardinalRoute.HomeSearch,
    screenWidthDp: Dp,
    screenHeightDp: Dp,
    appPreferenceRepository: AppPreferenceRepository,
) {
    val coroutineScope = rememberCoroutineScope()
    val searchExpanded: Boolean? by viewModel.searchExpanded.collectAsState(null)
    val bottomSheetState = rememberBottomSheetState(
        initialValue = BottomSheetValue.Collapsed
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)

    val focusManager = LocalFocusManager.current
    val imeController = LocalSoftwareKeyboardController.current

    if (searchExpanded == true) {
        BackHandler {
            viewModel.collapseSearch()
        }
    }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded == true) {
            bottomSheetState.expand()
        } else {
            imeController?.hide()
            focusManager.clearFocus(force = true)
            bottomSheetState.collapse()
        }
    }

    LaunchedEffect(Unit) {
        mapPins.clear()
    }

    CardinalAppScaffold(
        scaffoldState = scaffoldState,
        peekHeight = peekHeight,
        fabHeightCallback = {
            if (topOfBackStack == route) {
                onFabHeightChange(it)
            }
        },
        showToolbar = true,
        content = {
            HomeScreen(
                viewModel = viewModel,
                onPlaceSelected = { place ->
                    imeController?.hide()

                    // We are intentionally not collapsing search here, but we do set the bottom
                    // sheet state to collapsed to prevent jank on popping back to this screen.
                    coroutineScope.launch {
                        // This should happen before we navigate away otherwise we get a race condition
                        // between setting the anchors and collapsing the sheet. Unfortunately, it
                        // doesn't return until the sheet is fully collapsed, so we queue the navigation
                        // after this and hope for the best.
                        bottomSheetState.collapse()
                    }
                    coroutineScope.launch {
                        navigator.navigate(CardinalRoute.PlaceCard(place))
                    }
                },
                onPeekHeightChange = {
                    if (topOfBackStack == route) {
                        onPeekHeightChange(it)
                    }
                },
                onSearchFocusChange = {
                    if (it) {
                        viewModel.expandSearch()
                    }
                },
                onResultPinsChange = {
                    mapPins.clear()
                    mapPins.addAll(it)
                },
                onSearchEvent = {
                    viewModel.collapseSearch()
                    coroutineScope.launch {
                        animateToSearchResultsOnSubmit(
                            cameraState = cameraState,
                            searchPlaces = mapPins.toList(),
                            screenWidthDp = screenWidthDp,
                            screenHeightDp = screenHeightDp,
                            peekHeight = peekHeight,
                            animationDuration = appPreferenceRepository.animationSpeedDurationValue
                        )
                    }
                }
            )
        })
}

private suspend fun animateToSearchResultsOnSubmit(
    cameraState: CameraState,
    searchPlaces: List<Place>,
    screenWidthDp: Dp,
    screenHeightDp: Dp,
    peekHeight: Dp,
    animationDuration: kotlin.time.Duration,
) {
    val searchCenter = cameraState.position.target
    val boundingBox = searchPlaces.toBoundingBox() ?: return
    val searchPadding = searchResultPadding(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp,
        peekHeight = peekHeight
    )

    cameraState.animateTo(
        boundingBox = boundingBox.toGeoJsonBoundingBox(),
        padding = searchPadding,
        duration = animationDuration
    )

    if (cameraState.position.zoom < SEARCH_RESULTS_MIN_ZOOM_ON_SUBMIT) {
        cameraState.animateTo(
            cameraState.position.copy(
                target = searchCenter,
                zoom = SEARCH_RESULTS_MIN_ZOOM_ON_SUBMIT
            ),
            duration = animationDuration
        )
        fitNearbySearchResultsIfCappedViewIsEmpty(
            cameraState = cameraState,
            searchPlaces = searchPlaces,
            searchCenter = searchCenter,
            searchPadding = searchPadding,
            animationDuration = animationDuration
        )
    }
}

private fun searchResultPadding(
    screenWidthDp: Dp,
    screenHeightDp: Dp,
    peekHeight: Dp,
): PaddingValues {
    return PaddingValues(
        start = screenWidthDp / 8,
        top = screenHeightDp / 8,
        end = screenWidthDp / 8,
        bottom = min(
            3f * screenHeightDp / 4,
            peekHeight + screenHeightDp / 8
        )
    )
}

private suspend fun fitNearbySearchResultsIfCappedViewIsEmpty(
    cameraState: CameraState,
    searchPlaces: List<Place>,
    searchCenter: Position,
    searchPadding: PaddingValues,
    animationDuration: kotlin.time.Duration,
) {
    val visibleRegion = cameraState.projection?.queryVisibleRegion()
    if (searchPlaces.hasVisiblePoiIn(visibleRegion)) return

    val nearbyPositions: List<Position> = searchPlaces
        .nearestTo(searchCenter)
        .take(SEARCH_RESULTS_TO_FIT_WHEN_CAPPED_VIEW_IS_EMPTY)
        .map { it.toPosition() } + listOf(searchCenter)

    val nearbyBoundingBox = PolylineUtils.calculateBoundingBox(nearbyPositions)
        ?: return

    cameraState.animateTo(
        boundingBox = nearbyBoundingBox.toGeoJsonBoundingBox(),
        padding = searchPadding,
        duration = animationDuration
    )
}

private fun List<Place>.toBoundingBox(): CardinalBoundingBox? {
    return PolylineUtils.calculateBoundingBox(map { it.toPosition() })
}

private fun List<Place>.hasVisiblePoiIn(visibleRegion: VisibleRegion?): Boolean {
    return visibleRegion != null && any { visibleRegion.contains(it.toPosition()) }
}

private fun List<Place>.nearestTo(position: Position): List<Place> {
    val center = LatLng(position.latitude, position.longitude)
    return sortedBy { it.latLng.fastDistanceTo(center) }
}

private fun VisibleRegion.contains(position: Position): Boolean {
    val latitudes = listOf(
        farLeft.latitude,
        farRight.latitude,
        nearLeft.latitude,
        nearRight.latitude
    )
    val longitudes = listOf(
        farLeft.longitude,
        farRight.longitude,
        nearLeft.longitude,
        nearRight.longitude
    )

    return position.latitude in latitudes.min()..latitudes.max() &&
        position.longitude in longitudes.min()..longitudes.max()
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun CardinalAppScaffold(
    scaffoldState: BottomSheetScaffoldState,
    peekHeight: Dp,
    content: @Composable (SnackbarHostState) -> Unit,
    fabHeightCallback: (Dp) -> Unit,
    showToolbar: Boolean,
) {
    val density = LocalDensity.current
    val snackBarHostState = remember { SnackbarHostState() }
    val bottomInset = with(density) {
        WindowInsets.safeContent.getBottom(density).toDp()
    }
    val handleHeight = 48.dp

    // If toolbar is visible, add padding for it below the scaffold
    val bottomPadding = if (showToolbar && !WindowInsets.isImeVisible) TOOLBAR_HEIGHT_DP else 0.dp

    Box(modifier = Modifier.fillMaxSize()) {
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeight + bottomInset + handleHeight + bottomPadding,
            snackbarHost = { SnackbarHost(snackBarHostState) },
            sheetBackgroundColor = BottomSheetDefaults.ContainerColor,
            sheetContent = {
                Column(
                    modifier = Modifier.padding(bottom = bottomPadding).onGloballyPositioned {
                        fabHeightCallback(with(density) { it.positionInRoot().y.toDp() })
                    }) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minHeight = handleHeight)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        BottomSheetDefaults.DragHandle()
                    }
                    content(snackBarHostState)
                }
            },
            content = {})
    }

}

@Composable
fun BirdSettingsFab(navigator: CardinalNavigator) {
    // Avatar icon button in top left
    Box {
        FloatingActionButton(
            onClick = { navigator.navigate(CardinalRoute.Settings) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(dimensionResource(dimen.padding))
                .size(64.dp)
                .border(
                    width = 4.dp, color = MaterialTheme.colorScheme.surface, shape = CircleShape
                ),
            containerColor = colorResource(color.icon_background),
            shape = CircleShape
        ) {
            Image(
                modifier = Modifier.size(36.dp),
                painter = painterResource(drawable.cardinal_icon),
                contentDescription = "Cardinal Maps Settings",
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(dimensionResource(dimen.padding_minor))
                .size(24.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
            )
            Icon(
                modifier = Modifier.padding(4.dp),
                painter = painterResource(drawable.ic_settings),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPermissionDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.compose.material3.Text(text = stringResource(string.enable_location_title))
        },
        text = {
            androidx.compose.material3.Text(text = stringResource(string.enable_location_message))
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onAccept) {
                androidx.compose.material3.Text(text = stringResource(string.allow))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                androidx.compose.material3.Text(text = stringResource(string.not_now))
            }
        }
    )
}
