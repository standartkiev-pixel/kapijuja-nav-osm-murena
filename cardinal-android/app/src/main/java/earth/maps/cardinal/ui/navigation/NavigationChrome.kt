package earth.maps.cardinal.ui.navigation

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stadiamaps.ferrostar.composeui.config.NavigationViewComponentBuilder
import com.stadiamaps.ferrostar.composeui.formatting.DateTimeFormatter
import com.stadiamaps.ferrostar.composeui.formatting.DistanceMeasurementSystem
import com.stadiamaps.ferrostar.composeui.formatting.DistanceFormatter
import com.stadiamaps.ferrostar.composeui.formatting.DurationFormatter
import com.stadiamaps.ferrostar.composeui.formatting.EstimatedArrivalDateTimeFormatter
import com.stadiamaps.ferrostar.composeui.formatting.LocalizedDistanceFormatter
import com.stadiamaps.ferrostar.composeui.formatting.LocalizedDurationFormatter
import com.stadiamaps.ferrostar.composeui.models.CameraControlState
import com.stadiamaps.ferrostar.composeui.theme.DefaultInstructionRowTheme
import com.stadiamaps.ferrostar.composeui.theme.DefaultRoadNameViewTheme
import com.stadiamaps.ferrostar.composeui.theme.InstructionRowTheme
import com.stadiamaps.ferrostar.composeui.theme.NavigationUITheme
import com.stadiamaps.ferrostar.composeui.theme.RoadNameViewTheme
import com.stadiamaps.ferrostar.composeui.theme.TripProgressViewStyle
import com.stadiamaps.ferrostar.composeui.theme.TripProgressViewTheme
import com.stadiamaps.ferrostar.composeui.views.components.CurrentRoadNameView
import com.stadiamaps.ferrostar.composeui.views.components.InstructionsView
import com.stadiamaps.ferrostar.composeui.views.components.gridviews.InnerGridView
import com.stadiamaps.ferrostar.composeui.views.components.maneuver.ManeuverImage
import com.stadiamaps.ferrostar.composeui.views.components.speedlimit.SignageStyle
import com.stadiamaps.ferrostar.core.NavigationUiState
import com.stadiamaps.ferrostar.core.measurement.MeasurementSpeed
import com.stadiamaps.ferrostar.composeui.R as FerrostarR
import dagger.hilt.android.lifecycle.HiltViewModel
import earth.maps.cardinal.R
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.AppPreferences
import earth.maps.cardinal.routing.TrafficEtaCalibration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.TripProgress
import uniffi.ferrostar.VisualInstructionContent
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

object CardinalNavigationUITheme : NavigationUITheme {
    override val instructionRowTheme: InstructionRowTheme
        @Composable get() = DefaultInstructionRowTheme

    override val roadNameViewTheme: RoadNameViewTheme
        @Composable get() = DefaultRoadNameViewTheme

    override val tripProgressViewTheme: TripProgressViewTheme
        @Composable get() = CardinalTripProgressViewTheme

    override val buttonSize: DpSize
        @Composable get() = DpSize(56.dp, 56.dp)
}

object CardinalTripProgressViewTheme : TripProgressViewTheme {
    override val style: TripProgressViewStyle
        @Composable get() = TripProgressViewStyle.SIMPLIFIED

    override val measurementTextStyle: TextStyle
        @Composable
        get() =
            MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Normal)

    override val secondaryTextStyle: TextStyle
        @Composable
        get() =
            MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    override val exitIconColor: Color
        @Composable get() = MaterialTheme.colorScheme.onSecondary

    override val exitButtonBackgroundColor: Color
        @Composable get() = MaterialTheme.colorScheme.secondary

    override val backgroundColor: Color
        @Composable get() = MaterialTheme.colorScheme.surface
}


@Composable
fun CardinalInstructionsView(modifier: Modifier, uiState: NavigationUiState) {
    val viewModel: NavigationChromeViewModel = hiltViewModel()
    val distanceUnit by viewModel.distanceUnits.collectAsState()
    val distanceMeasurementSystem = if (distanceUnit == AppPreferences.DISTANCE_UNIT_METRIC) {
        DistanceMeasurementSystem.SI
    } else {
        DistanceMeasurementSystem.IMPERIAL
    }
    val formatter = remember(distanceMeasurementSystem) { LocalizedDistanceFormatter(distanceMeasurementSystemOverride = distanceMeasurementSystem) }
    val currentStep = remember(uiState.remainingSteps, uiState.visualInstruction) {
        uiState.remainingSteps?.stepForInstruction(uiState.visualInstruction)
    }
    val remainingSteps = remember(uiState.remainingSteps) {
        uiState.remainingSteps?.map(RouteStep::patchRoundaboutInstructionIcons)
    }
    val instructionTheme = CardinalNavigationUITheme.instructionRowTheme

    uiState.visualInstruction?.let { instructions ->
        InstructionsView(
            modifier = modifier,
            instructions = instructions.patchRoundaboutInstructionIcons(currentStep),
            theme = instructionTheme,
            remainingSteps = remainingSteps,
            distanceFormatter = formatter,
            distanceToNextManeuver = uiState.progress?.distanceToNextManeuver,
            contentBuilder = { instruction ->
                CardinalManeuverImage(
                    content = instruction.primaryContent,
                    tint = instructionTheme.iconTintColor,
                )
            },
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun CardinalProgressView(
    modifier: Modifier,
    uiState: NavigationUiState,
    onTapExit: (() -> Unit)?
) {
    val viewModel: NavigationChromeViewModel = hiltViewModel()
    val turnByTurnViewModel: TurnByTurnNavigationViewModel = hiltViewModel()
    val now by viewModel.now.collectAsState()
    val distanceUnit by viewModel.distanceUnits.collectAsState()
    val navigationState by turnByTurnViewModel.state.collectAsState()
    val distanceMeasurementSystem = if (distanceUnit == AppPreferences.DISTANCE_UNIT_METRIC) {
        DistanceMeasurementSystem.SI
    } else {
        DistanceMeasurementSystem.IMPERIAL
    }
    val distanceFormatter = remember(distanceMeasurementSystem) { LocalizedDistanceFormatter(distanceMeasurementSystemOverride = distanceMeasurementSystem) }
    uiState.progress?.let { progress ->
        CardinalTripProgressView(
            modifier = modifier,
            theme = CardinalNavigationUITheme.tripProgressViewTheme,
            progress = progress,
            onTapExit = onTapExit,
            distanceFormatter = distanceFormatter,
            fromDate = now,
            etaCorrectionFactor = navigationState.etaCorrectionFactor,
        )
    }
}

@OptIn(ExperimentalTime::class)
@Composable
private fun CardinalTripProgressView(
    modifier: Modifier = Modifier,
    theme: TripProgressViewTheme,
    estimatedArrivalFormatter: DateTimeFormatter = EstimatedArrivalDateTimeFormatter(),
    distanceFormatter: DistanceFormatter = LocalizedDistanceFormatter(),
    durationFormatter: DurationFormatter = LocalizedDurationFormatter(),
    progress: TripProgress,
    fromDate: Instant = Clock.System.now(),
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    etaCorrectionFactor: Double = TrafficEtaCalibration.NO_CORRECTION_FACTOR,
    onTapExit: (() -> Unit)? = null
) {
    val pillShape = RoundedCornerShape(50)
    val correctedDurationRemaining = TrafficEtaCalibration.correctedDurationSeconds(
        rawDurationSeconds = progress.durationRemaining,
        correctionFactor = etaCorrectionFactor
    )

    Box(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier
                    .shadow(12.dp, shape = pillShape)
                    .border(
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        shape = pillShape
                    )
                    .background(color = theme.backgroundColor, shape = pillShape)
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavigationProgressValue(
                    modifier = Modifier.weight(1f),
                    text = estimatedArrivalFormatter.format(
                        (fromDate + correctedDurationRemaining.seconds).toLocalDateTime(timeZone)
                    ),
                    style = theme.measurementTextStyle
                )

                NavigationProgressValue(
                    modifier = Modifier.weight(1f),
                    text = durationFormatter.format(correctedDurationRemaining),
                    style = theme.measurementTextStyle
                )

                NavigationProgressValue(
                    modifier = Modifier.weight(1f),
                    text = distanceFormatter.format(progress.distanceRemaining),
                    style = theme.measurementTextStyle
                )

                if (onTapExit != null) {
                    Button(
                        onClick = onTapExit,
                        modifier = Modifier.size(50.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.exitButtonBackgroundColor
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = stringResource(id = FerrostarR.string.end_navigation),
                            tint = theme.exitIconColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationProgressValue(
    modifier: Modifier = Modifier,
    text: String,
    style: TextStyle
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1
        )
    }
}

@Composable
fun CardinalRoadNameView(
    modifier: Modifier,
    roadName: String?,
    cameraControlState: CameraControlState
) {
    if (cameraControlState is CameraControlState.ShowRouteOverview) {
        roadName?.let { roadName ->
            Row(
                modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                CurrentRoadNameView(
                    modifier = modifier,
                    theme = CardinalNavigationUITheme.roadNameViewTheme,
                    currentRoadName = roadName
                )

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun CurrentSpeedNavigationOverlay(
    modifier: Modifier,
    currentSpeed: NavigationSpeedUi?,
    speedLimit: MeasurementSpeed?,
    distanceUnit: Int,
    instructionHeight: Dp,
    progressHeight: Dp,
    showOfflineWarning: Boolean = false
) {
    if (currentSpeed == null && speedLimit == null && !showOfflineWarning) {
        return
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val signageStyle = if (distanceUnit == AppPreferences.DISTANCE_UNIT_METRIC) {
        SignageStyle.ViennaConvention
    } else {
        SignageStyle.MUTCD
    }
    val speedStack: @Composable () -> Unit = {
        if (currentSpeed != null || speedLimit != null) {
            NavigationSpeedStack(
                currentSpeed = currentSpeed,
                speedLimit = speedLimit,
                distanceUnit = distanceUnit,
                signageStyle = signageStyle
            )
        }
    }

    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
            Spacer(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.5f)
            )
            Spacer(modifier = Modifier.width(NAVIGATION_GRID_SPACING))
            InnerGridView(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(end = NAVIGATION_GRID_SPACING),
                topStart = speedStack,
                bottomCenter = {
                    OfflineWarningAboveRoadName(
                        showOfflineWarning = showOfflineWarning,
                        progressHeight = progressHeight
                    )
                }
            )
        }
    } else {
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(instructionHeight))
            InnerGridView(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f, fill = true)
                    .padding(NAVIGATION_GRID_SPACING),
                topStart = speedStack,
                bottomCenter = {
                    OfflineWarningAboveRoadName(
                        showOfflineWarning = showOfflineWarning,
                        progressHeight = progressHeight
                    )
                }
            )
        }
    }
}

@Composable
private fun OfflineWarningAboveRoadName(
    showOfflineWarning: Boolean,
    progressHeight: Dp
) {
    if (!showOfflineWarning) {
        return
    }
    val bottomProgressHeight = if (progressHeight == 0.dp) {
        NAVIGATION_PROGRESS_FALLBACK_HEIGHT
    } else {
        progressHeight
    }

    NavigationOfflineWarning(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = NAVIGATION_GRID_SPACING,
                end = NAVIGATION_GRID_SPACING,
                bottom = bottomProgressHeight + NAVIGATION_ROAD_NAME_CLEARANCE
            )
    )
}

@Composable
private fun NavigationSpeedStack(
    currentSpeed: NavigationSpeedUi?,
    speedLimit: MeasurementSpeed?,
    distanceUnit: Int,
    signageStyle: SignageStyle
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        speedLimit?.let { limit ->
            val speedLimitUi = limit.toSpeedLimitUi(distanceUnit) ?: return@let
            SpeedLimitSign(
                speedLimit = limit,
                signageStyle = signageStyle,
                contentDescription = stringResource(
                    R.string.speed_limit_content_description,
                    speedLimitUi.displayText
                )
            )
        }

        if (speedLimit != null && currentSpeed != null) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        currentSpeed?.let { speed ->
            CurrentSpeedSign(
                speed = speed,
                contentDescription = stringResource(
                    R.string.current_speed_content_description,
                    speed.displayText
                )
            )
        }
    }
}

private val NAVIGATION_GRID_SPACING = 16.dp
private val NAVIGATION_ROAD_NAME_CLEARANCE = 72.dp
private val NAVIGATION_PROGRESS_FALLBACK_HEIGHT = 88.dp

fun navigationViewComponentBuilder(
    onInstructionsHeightChanged: (Dp) -> Unit = {},
    onProgressHeightChanged: (Dp) -> Unit = {},
    customOverlayView: (@Composable BoxScope.(Modifier) -> Unit)? = null
): NavigationViewComponentBuilder {
    return NavigationViewComponentBuilder(
        instructionsView = @Composable { modifier, navigationUiState ->
            val density = LocalDensity.current
            Box(
                modifier = modifier.onSizeChanged { size ->
                    onInstructionsHeightChanged(with(density) { size.height.toDp() })
                }
            ) {
                CardinalInstructionsView(
                    Modifier,
                    navigationUiState
                )
            }
        },
        progressView = @Composable { modifier, navigationUiState, onTapExit ->
            val density = LocalDensity.current
            CardinalProgressView(
                modifier.onSizeChanged { size ->
                    onProgressHeightChanged(with(density) { size.height.toDp() })
                },
                navigationUiState,
                onTapExit
            )
        },
        roadNameView = @Composable { modifier, roadName, cameraControlState ->
            CardinalRoadNameView(
                modifier,
                roadName,
                cameraControlState
            )
        },
        customOverlayView = customOverlayView,
    )
}

@OptIn(ExperimentalTime::class)
@HiltViewModel
class NavigationChromeViewModel @Inject constructor(
    appPreferences: AppPreferenceRepository
): ViewModel() {

    val now = MutableStateFlow(Clock.System.now())
    val distanceUnits = appPreferences.distanceUnit

    private var cleared = false

    init {
        viewModelScope.launch {
            while (!cleared) {
                now.value = Clock.System.now()
                delay(DELAY)
            }
        }
    }

    override fun onCleared() {
        cleared = true
        super.onCleared()
    }

    companion object {
        private val DELAY = 5.seconds
    }
}

@Composable
private fun CardinalManeuverImage(content: VisualInstructionContent, tint: Color) {
    val resourceId = content.cardinalManeuverIcon()

    if (resourceId != null) {
        Icon(
            painter = painterResource(id = resourceId),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(64.dp),
        )
    } else {
        ManeuverImage(content = content, tint = tint)
    }
}
