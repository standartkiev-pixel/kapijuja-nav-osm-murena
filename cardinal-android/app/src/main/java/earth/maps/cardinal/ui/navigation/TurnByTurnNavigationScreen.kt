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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maplibre.compose.camera.CameraState
import com.maplibre.compose.rememberSaveableMapViewCamera
import com.stadiamaps.ferrostar.composeui.config.VisualNavigationViewConfig
import com.stadiamaps.ferrostar.core.DefaultNavigationViewModel
import com.stadiamaps.ferrostar.core.annotation.valhalla.valhallaExtendedOSRMAnnotationPublisher
import com.stadiamaps.ferrostar.maplibreui.runtime.navigationMapViewCamera
import com.stadiamaps.ferrostar.maplibreui.views.DynamicallyOrientingNavigationView
import earth.maps.cardinal.R
import earth.maps.cardinal.data.RoutingMode
import earth.maps.cardinal.data.formatDuration
import kotlinx.coroutines.delay
import uniffi.ferrostar.Route

const val NAVIGATION_OFFLINE_WARNING_TEST_TAG = "navigation_offline_warning"
private const val NAVIGATION_AUTO_RECENTER_DELAY_MILLIS = 15_000L

@Composable
fun KeepScreenOn() {
    val currentView = LocalView.current
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose {
            currentView.keepScreenOn = false
        }
    }
}

@Composable
fun TurnByTurnNavigationScreen(
    port: Int,
    mode: RoutingMode,
    route: Route?,
    accessApproachRoute: Route? = null,
    useDarkTheme: Boolean
) {
    val context = LocalContext.current
    // Inject the ViewModel using Hilt
    val turnByTurnViewModel: TurnByTurnNavigationViewModel = hiltViewModel()
    val navigationChromeViewModel: NavigationChromeViewModel = hiltViewModel()
    val currentSpeedViewModel: CurrentSpeedViewModel = hiltViewModel()
    val ferrostarWrapperRepository = turnByTurnViewModel.ferrostarWrapperRepository
    val distanceUnit by navigationChromeViewModel.distanceUnits.collectAsStateWithLifecycle()
    val navigationState by turnByTurnViewModel.state.collectAsStateWithLifecycle()
    var instructionsHeight by remember { mutableStateOf(0.dp) }
    var progressHeight by remember { mutableStateOf(0.dp) }

    // Get the appropriate FerrostarWrapper based on routing mode
    val ferrostarWrapper = when (mode) {
        RoutingMode.AUTO -> ferrostarWrapperRepository.driving
        RoutingMode.PEDESTRIAN -> ferrostarWrapperRepository.walking
        RoutingMode.BICYCLE -> ferrostarWrapperRepository.cycling
        RoutingMode.TRUCK -> ferrostarWrapperRepository.truck
        RoutingMode.BUS -> ferrostarWrapperRepository.bus
        RoutingMode.MOTOR_SCOOTER -> ferrostarWrapperRepository.motorScooter
        RoutingMode.MOTORCYCLE -> ferrostarWrapperRepository.motorcycle
        else -> null
    }
    if (ferrostarWrapper == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Unsupported routing mode",
                style = MaterialTheme.typography.headlineSmall
            )
        }
        return
    } else {
        val ferrostarCore = ferrostarWrapper.core

        // Start navigation when a route is provided
        DisposableEffect(route) {
            route?.let {
                turnByTurnViewModel.startNavigation(ferrostarWrapper, it)
            }
            onDispose {
                route?.let {
                    turnByTurnViewModel.stopNavigation()
                }
            }
        }

        // TODO: Make this configurable.
        KeepScreenOn()

        // Create and remember the navigation view model
        val viewModel = remember(ferrostarCore) {
            DefaultNavigationViewModel(
                ferrostarCore = ferrostarCore,
                annotationPublisher = valhallaExtendedOSRMAnnotationPublisher()
            )
        }
        val ferrostarUiState by viewModel.navigationUiState.collectAsStateWithLifecycle()
        val currentSpeed by currentSpeedViewModel.currentSpeed.collectAsStateWithLifecycle()

        // Determine the style URL based on theme
        val styleVariant = if (useDarkTheme) "dark" else "light"
        val styleUrl = "http://127.0.0.1:$port/style_$styleVariant.json"

        // Only display the navigation view if we have a route
        if (route != null) {
            val activeTrafficRoute = rememberUpdatedState(navigationState.activeRoute ?: route)
            val trafficAvailable = rememberUpdatedState(navigationState.trafficAvailable)
            val navigationCamera = navigationMapViewCamera()
            val latestNavigationCamera by rememberUpdatedState(navigationCamera)
            val camera = rememberSaveableMapViewCamera()
            var pointerInteractionActive by remember { mutableStateOf(false) }
            val isOffGuidance =
                camera.value.state !is CameraState.TrackingUserLocationWithBearing

            // Route-only TomTom-style return to Guidance: browsing is never interrupted
            // without a route. While the driver is touching the screen the countdown is
            // suspended; every new interaction therefore restarts the full 15-second delay.
            LaunchedEffect(route, isOffGuidance, pointerInteractionActive) {
                if (isOffGuidance && !pointerInteractionActive) {
                    delay(NAVIGATION_AUTO_RECENTER_DELAY_MILLIS)
                    if (camera.value.state !is CameraState.TrackingUserLocationWithBearing) {
                        camera.value = latestNavigationCamera
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(route) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                pointerInteractionActive = event.changes.any { it.pressed }
                            }
                        }
                    }
            ) {
                DynamicallyOrientingNavigationView(
                    styleUrl = styleUrl,
                    modifier = Modifier.fillMaxSize(),
                    camera = camera,
                    navigationCamera = navigationCamera,
                    viewModel = viewModel,
                    config = VisualNavigationViewConfig.Default(),
                    views = navigationViewComponentBuilder(
                        onInstructionsHeightChanged = { height ->
                            instructionsHeight = height
                        },
                        onProgressHeightChanged = { height ->
                            progressHeight = height
                        },
                        customOverlayView = { overlayModifier ->
                            if (ferrostarUiState.progress != null) {
                                CurrentSpeedNavigationOverlay(
                                    modifier = overlayModifier,
                                    currentSpeed = currentSpeed,
                                    speedLimit = ferrostarUiState.currentAnnotation?.speedLimit,
                                    distanceUnit = distanceUnit,
                                    instructionHeight = instructionsHeight,
                                    progressHeight = progressHeight,
                                    showOfflineWarning = !navigationState.isInternetConnected
                                )
                            }
                        }
                    ),
                    mapContent = { uiState ->
                        TrafficRouteMapOverlay(
                            context = context,
                            route = activeTrafficRoute.value,
                            remainingSteps = uiState.remainingSteps,
                            trafficAvailable = trafficAvailable.value,
                            accessApproachRoute = accessApproachRoute
                        )
                    }
                )

                navigationState.rerouteSuggestion?.let { suggestion ->
                    FasterRoutePrompt(
                        timeSavingsSeconds = suggestion.timeSavingsSeconds,
                        onAccept = turnByTurnViewModel::acceptReroute,
                        onDismiss = turnByTurnViewModel::dismissReroute,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(16.dp)
                    )
                }
            }
        } else {
            // Show a placeholder or loading state when no route is available
            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No route available for navigation",
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

@Composable
fun NavigationOfflineWarning(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(NAVIGATION_OFFLINE_WARNING_TEST_TAG),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 3.dp
    ) {
        Text(
            text = stringResource(R.string.navigation_offline_reroute_warning),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun FasterRoutePrompt(
    timeSavingsSeconds: Int,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.faster_route_available,
                    formatDuration(timeSavingsSeconds)
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dismiss_button))
                }
                Button(onClick = onAccept) {
                    Text(stringResource(R.string.switch_route))
                }
            }
        }
    }
}
