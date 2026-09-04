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

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.zIndex
import earth.maps.cardinal.R
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.R.drawable
import earth.maps.cardinal.R.string
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.PolylineUtils
import earth.maps.cardinal.data.desaturate
import earth.maps.cardinal.data.formatDuration
import earth.maps.cardinal.data.parseRouteColor
import earth.maps.cardinal.data.room.OfflineArea
import earth.maps.cardinal.routing.HeavyVehicleAccessApproach
import earth.maps.cardinal.routing.HeavyVehicleAccessRelaxation
import earth.maps.cardinal.routing.TrafficEtaCalibration
import earth.maps.cardinal.routing.TrafficLevel
import earth.maps.cardinal.routing.TrafficRouteSegments
import earth.maps.cardinal.routing.navigationHexColor
import earth.maps.cardinal.transit.Itinerary
import earth.maps.cardinal.transit.Leg
import earth.maps.cardinal.transit.Mode
import earth.maps.cardinal.ui.map.LocationPuck
import earth.maps.cardinal.ui.util.RouteDurationLabel
import earth.maps.cardinal.ui.util.RouteDurationLabelPlacer
import earth.maps.cardinal.ui.util.RouteLabelBounds
import earth.maps.cardinal.ui.util.defaultTransitModeColor
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.maplibre.compose.camera.CameraState
import org.maplibre.compose.expressions.dsl.Feature.get
import org.maplibre.compose.expressions.dsl.Feature.has
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.nil
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.rgbColor
import org.maplibre.compose.expressions.value.IconTextFit
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.OrnamentOptions
import org.maplibre.compose.map.RenderOptions
import org.maplibre.compose.material3.DisappearingCompassButton
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonSource
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.style.rememberStyleState
import org.maplibre.compose.util.ClickResult
import org.maplibre.compose.util.VisibleRegion
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.LineString
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Polygon
import org.maplibre.spatialk.geojson.Position
import org.maplibre.spatialk.geojson.dsl.buildFeatureCollection
import uniffi.ferrostar.Route
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds
@Composable
fun MapView(
    port: Int,
    mapViewModel: MapViewModel,
    onMapPoiClick: (Place) -> Unit,
    onMapInteraction: () -> Unit,
    onDropPin: (LatLng) -> Unit,
    onRequestLocationPermission: () -> Unit,
    hasLocationPermission: Boolean,
    mapPins: List<Place>,
    fabInsets: PaddingValues,
    cameraState: CameraState,
    screenWidthDp: Dp,
    screenHeightDp: Dp,
    appPreferences: AppPreferenceRepository,
    useDarkTheme: Boolean,
    selectedOfflineArea: OfflineArea? = null,
    currentRoute: Route? = null,
    allRoutes: List<Route>,
    trafficAvailable: Boolean = false,
    etaCorrectionFactor: Double = 1.0,
    accessApproach: HeavyVehicleAccessApproach? = null,
    currentTransitItinerary: Itinerary? = null,
    highlightedTransitLegIndex: Int? = null,
    onRouteAnnotationClick: ((Int) -> Unit)? = null,
) {
    val context = LocalContext.current
    val styleState = rememberStyleState()
    val pinFeatures = mapPins.map { mapViewModel.createFeatureFromPlace(it) }
    rememberCoroutineScope()

    val styleVariant = if (useDarkTheme) "dark" else "light"

    // Load saved viewport on initial composition
    LaunchedEffect(Unit) {
        val savedViewport = mapViewModel.loadViewport()
        if (savedViewport != null) {
            cameraState.animateTo(savedViewport, duration = 1.milliseconds)
        }
    }

    // Save viewport when composition is disposed
    DisposableEffect(cameraState) {
        onDispose {
            // Save current viewport
            mapViewModel.saveViewport(cameraState.position)
        }
    }

    // Update viewport center when camera position changes
    LaunchedEffect(cameraState.position) {
        mapViewModel.updateViewportCenter(cameraState.position)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Validate port before using it in URL
        if (port > 0 && port < 65536) {
            MaplibreMap(
                modifier = Modifier.fillMaxSize(),
                cameraState = cameraState,
                baseStyle = BaseStyle.Uri("http://127.0.0.1:$port/style_$styleVariant.json"),
                styleState = styleState,
                options = MapOptions(
                    ornamentOptions = OrnamentOptions.AllDisabled.copy(
                        padding = fabInsets,
                        isAttributionEnabled = true,
                        attributionAlignment = Alignment.BottomStart
                    ), renderOptions = RenderOptions()
                ),
                onMapClick = { position, dpOffset ->
                    mapViewModel.handleMapTap(
                        cameraState,
                        dpOffset,
                        onMapPoiClick,
                        onMapInteraction,
                        onRouteAnnotationClick,
                    )
                    ClickResult.Consume
                },
                onMapLongClick = { position, dpOffset ->
                    onDropPin(LatLng(position.latitude, position.longitude))
                    ClickResult.Consume
                }) {
                val location by mapViewModel.locationFlow.collectAsState()
                val sensorHeading by mapViewModel.heading.collectAsState()
                val savedPlaces by mapViewModel.savedPlacesFlow.collectAsState(FeatureCollection())
                FavoritesLayer(savedPlaces, mapPins, useDarkTheme)

                OfflineBoundsLayer(selectedOfflineArea)

                RouteLayer(
                    mapViewModel,
                    currentRoute,
                    allRoutes,
                    trafficAvailable,
                    etaCorrectionFactor,
                    accessApproach,
                    visibleRegion = cameraState.projection?.queryVisibleRegion(),
                    screenWidthDp = screenWidthDp,
                    screenHeightDp = screenHeightDp
                )

                TransitLayer(
                    currentTransitItinerary = currentTransitItinerary,
                    highlightedTransitLegIndex = highlightedTransitLegIndex,
                )

                PinsLayer(pinFeatures, useDarkTheme)

                location?.let { LocationPuck(it, sensorHeading) }
            }
        } else {
            // Handle invalid port - could show an error message
            Box(modifier = Modifier.fillMaxSize()) {
                // Error UI could be added here
            }
        }

        // Handle permission state changes
        LaunchedEffect(hasLocationPermission) {
            mapViewModel.handlePermissionStateChange(hasLocationPermission, cameraState, context)
        }

        MapControls(
            cameraState = cameraState,
            mapViewModel = mapViewModel,
            fabInsets = fabInsets,
            hasLocationPermission = hasLocationPermission,
            onRequestLocationPermission = onRequestLocationPermission,
            appPreferences = appPreferences,
            context = context
        )
    }
}

@Composable
private fun FavoritesLayer(
    savedPlaces: FeatureCollection<Point, Map<String, JsonElement>>,
    activeMarkers: List<Place>,
    useDarkTheme: Boolean
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val activeMarkerIds = activeMarkers.mapNotNull { it.id }
    SymbolLayer(
        id = "user_favorites",
        source = rememberGeoJsonSource(GeoJsonData.JsonString(Json.encodeToString(savedPlaces))),
        iconAllowOverlap = const(true),
        iconImage = image(
            if (useDarkTheme) {
                painterResource(drawable.ic_stars_dark)
            } else {
                painterResource(drawable.ic_stars_light)
            }
        ),
        // Make the icon transparent if there's a pin symbol above it.
        iconOpacity = if (activeMarkerIds.isNotEmpty()) {
            org.maplibre.compose.expressions.dsl.switch(
                input = feature["saved_poi_id"].asString(),
                org.maplibre.compose.expressions.dsl.case(
                    activeMarkers.mapNotNull { it.id },
                    const(0f),
                ),
                fallback = const(1f)
            )
        } else {
            const(1f)
        },
        iconSize = const(0.8f),
        textField = feature["name"].asString(),
        textSize = const(0.8.em),
        textColor = rgbColor(
            const((textColor.red * 255.0f).toInt()),
            const((textColor.green * 255.0f).toInt()),
            const((textColor.blue * 255.0f).toInt()),
        ),
        textAnchor = const(SymbolAnchor.Top),
        textOffset = offset(0.em, 0.8.em),
        textOptional = const(true),
    )
}

@Composable
private fun OfflineBoundsLayer(selectedOfflineArea: OfflineArea?) {
    selectedOfflineArea?.let { area ->
        val boundsPolygon = Polygon(
            listOf(
                listOf(
                    Position(area.west, area.north),  // Northwest
                    Position(area.east, area.north),  // Northeast
                    Position(area.east, area.south),  // Southeast
                    Position(area.west, area.south),  // Southwest
                    Position(area.west, area.north)   // Close the polygon
                )
            )
        )
        val boundsFeature = Feature(geometry = boundsPolygon, properties = null)
        val offlineDownloadBoundsSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(
                Json.encodeToString(
                    buildFeatureCollection {
                        add(boundsFeature)
                    }
                )
            )
        )

        val color = MaterialTheme.colorScheme.onSurface
        LineLayer(
            id = "offline_download_bounds", source = offlineDownloadBoundsSource, color = rgbColor(
                const((color.red * 255).toInt()),
                const((color.green * 255).toInt()),
                const((color.blue * 255).toInt())
            ), width = const(3.dp)
        )
    }
}

@Composable
private fun RouteLayer(
    viewModel: MapViewModel,
    currentRoute: Route?,
    allRoutes: List<Route>,
    trafficAvailable: Boolean,
    etaCorrectionFactor: Double,
    accessApproach: HeavyVehicleAccessApproach?,
    visibleRegion: VisibleRegion?,
    screenWidthDp: Dp,
    screenHeightDp: Dp
) {
    val context = LocalContext.current
    val annotations = remember(allRoutes) { viewModel.placeRouteAnnotations(allRoutes) }
    val routeLabelBounds = remember(visibleRegion) {
        visibleRegion?.toRouteLabelBounds()
    }
    val minLabelDistanceMeters = remember(visibleRegion, screenWidthDp, screenHeightDp) {
        visibleRegion?.routeLabelMinDistanceMeters(screenWidthDp, screenHeightDp)
    }
    val routeDurationLabels = remember(allRoutes, annotations, routeLabelBounds, minLabelDistanceMeters) {
        val labelPlacer = minLabelDistanceMeters?.let(::RouteDurationLabelPlacer)
            ?: RouteDurationLabelPlacer()
        labelPlacer.place(
            routes = allRoutes,
            preferredPositions = annotations,
            visibleBounds = routeLabelBounds
        )
    }
    val trafficSegments = remember(currentRoute, trafficAvailable) {
        currentRoute
            ?.takeIf { trafficAvailable }
            ?.let(TrafficRouteSegments::build)
            .orEmpty()
    }

    // Create all route features in a single collection
    val routeFeatures = allRoutes.reversed().mapIndexed { index, route ->
        val routePositions = route.geometry.map { coord ->
            Position(coord.lng, coord.lat) // [longitude, latitude]
        }
        val routeLineString = LineString(routePositions)
        val desaturateAmount = 0.8f
        val polylineCasingColor = if (route == currentRoute) {
            formatColorAsJson(colorResource(R.color.polyline_casing_color))
        } else {
            formatColorAsJson(
                colorResource(R.color.polyline_casing_color).desaturate(desaturateAmount)
            )
        }
        val polylineColor = if (route == currentRoute) {
            formatColorAsJson(colorResource(R.color.polyline_color))
        } else {
            formatColorAsJson(
                colorResource(R.color.polyline_color).desaturate(desaturateAmount)
            )
        }
        Feature(
            geometry = routeLineString,
            properties = mapOf(
                "routeIndex" to Json.encodeToJsonElement(index.toString()),
                "routeColor" to polylineColor,
                "routeColorCasing" to polylineCasingColor,
                if (route == currentRoute) {
                    "current" to Json.encodeToJsonElement(true)
                } else {
                    "notCurrent" to Json.encodeToJsonElement(true)
                }
            )
        )
    }.toMutableList()


    // Create single source for all routes
    val routeSource = rememberGeoJsonSource(
        GeoJsonData.JsonString(Json.encodeToString(FeatureCollection(features = routeFeatures)))
    )

    // Route casing layer
    LineLayer(
        id = "route_lines_casing", source = routeSource,
        color = get("routeColorCasing").cast(),
        width = const(11.dp),
        opacity = const(1f),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )

    // Route main line layer
    LineLayer(
        id = "route_lines", source = routeSource,
        color = get("routeColor").cast(),
        width = const(8.dp),
        opacity = const(1f),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )

    accessApproach?.route?.geometry?.takeIf { it.size >= 2 }?.let { accessGeometry ->
        val accessFeature = Feature(
            geometry = LineString(
                accessGeometry.map { coordinate -> Position(coordinate.lng, coordinate.lat) }
            ),
            properties = null
        )
        val accessSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(
                Json.encodeToString(
                    FeatureCollection(features = listOf(accessFeature))
                )
            )
        )

        if (accessApproach.relaxation == HeavyVehicleAccessRelaxation.ROUTABLE_SNAP) {
            // Same strict profile reached this road point. Draw it exactly like the ordinary
            // selected route; no warning semantics are implied.
            val casing = colorResource(R.color.polyline_casing_color)
            val line = colorResource(R.color.polyline_color)
            LineLayer(
                id = "heavy_vehicle_access_approach_casing",
                source = accessSource,
                color = rgbColor(
                    const((casing.red * 255).toInt()),
                    const((casing.green * 255).toInt()),
                    const((casing.blue * 255).toInt())
                ),
                width = const(9.dp),
                opacity = const(1f),
                cap = const(LineCap.Round),
                join = const(LineJoin.Round),
            )
            LineLayer(
                id = "heavy_vehicle_access_approach",
                source = accessSource,
                color = rgbColor(
                    const((line.red * 255).toInt()),
                    const((line.green * 255).toInt()),
                    const((line.blue * 255).toInt())
                ),
                width = const(6.dp),
                opacity = const(1f),
                cap = const(LineCap.Round),
                join = const(LineJoin.Round),
            )
        } else {
            val accessStyle = accessApproach.relaxation.previewStyle()
            LineLayer(
                id = "heavy_vehicle_access_approach_casing",
                source = accessSource,
                color = rgbColor(const(40), const(40), const(40)),
                dasharray = const(accessStyle.dashArray),
                width = const(10.dp),
                opacity = const(0.9f),
                cap = const(LineCap.Round),
                join = const(LineJoin.Round),
            )
            LineLayer(
                id = "heavy_vehicle_access_approach",
                source = accessSource,
                color = rgbColor(
                    const(accessStyle.red),
                    const(accessStyle.green),
                    const(accessStyle.blue)
                ),
                dasharray = const(accessStyle.dashArray),
                width = const(6.dp),
                opacity = const(1f),
                cap = const(LineCap.Round),
                join = const(LineJoin.Round),
            )
        }
    }

    // Route casing layer
    LineLayer(
        id = "route_lines_casing_selected", source = routeSource,
        color = get("routeColorCasing").cast(),
        filter = has("current"),
        width = const(9.dp),
        opacity = const(1f),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )

    // Route main line layer
    LineLayer(
        id = "route_lines_selected", source = routeSource,
        color = get("routeColor").cast(),
        filter = has("current"),
        width = const(6.dp),
        opacity = const(1f),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )

    if (trafficSegments.isNotEmpty()) {
        val trafficFeatures = trafficSegments.map { segment ->
            Feature(
                geometry = LineString(
                    segment.coordinates.map { coord ->
                        Position(coord.lng, coord.lat)
                    }
                ),
                properties = mapOf(
                    "trafficColor" to trafficColorAsJson(context, segment.level)
                )
            )
        }
        val trafficSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(
                Json.encodeToString(FeatureCollection(features = trafficFeatures))
            )
        )

        LineLayer(
            id = "route_lines_traffic",
            source = trafficSource,
            color = get("trafficColor").cast(),
            width = const(6.dp),
            opacity = const(1f),
            cap = const(LineCap.Round),
            join = const(LineJoin.Round),
        )
    }

    RouteAnnotations(
        annotations = routeDurationLabels,
        etaCorrectionFactor = etaCorrectionFactor
    )
}

private fun trafficColorAsJson(
    context: Context,
    level: TrafficLevel
): JsonElement =
    Json.encodeToJsonElement(level.navigationHexColor(context))

@Composable
private fun formatColorAsJson(polylineCasingColor: Color): JsonElement = Json.encodeToJsonElement(
    "#${
        String.format(
            "%02x%02x%02x",
            (polylineCasingColor.red * 255).toInt(),
            (polylineCasingColor.green * 255).toInt(),
            (polylineCasingColor.blue * 255).toInt()
        )
    }"
)

@Composable
private fun RouteAnnotations(
    annotations: List<RouteDurationLabel>,
    etaCorrectionFactor: Double
) {
    val features = annotations.map { annotation ->
        val duration = formatDuration(
            TrafficEtaCalibration.correctedRouteDurationSecondsInt(
                route = annotation.route,
                correctionFactor = etaCorrectionFactor
            )
        )
        return@map Feature(
            geometry = Point(
                coordinates = Position(
                    longitude = annotation.position.longitude,
                    latitude = annotation.position.latitude
                )
            ),
            properties = mapOf(
                "routeIndex" to Json.encodeToJsonElement(annotation.routeIndex.toString()),
                "duration" to Json.encodeToJsonElement(duration)
            )
        )
    }
    val annotationSource = rememberGeoJsonSource(
        GeoJsonData.JsonString(Json.encodeToString(FeatureCollection(features = features)))
    )

    SymbolLayer(
        id = "route_annotations",
        source = annotationSource,
        iconImage = image(painterResource(drawable.route_duration_badge)),
        iconTextFit = const(IconTextFit.Both),
        iconTextFitPadding = const(PaddingValues.Absolute(8.dp, 4.dp, 8.dp, 4.dp)),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        iconAnchor = const(SymbolAnchor.Center),
        textField = org.maplibre.compose.expressions.dsl.Feature["duration"].cast(),
        textAnchor = const(SymbolAnchor.Center),
        textColor = rgbColor(
            const(255),
            const(255),
            const(255)
        ),
        textFont = const(listOf("Noto Sans Medium")),
        textSize = const(0.72.em),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
    )
}

private data class HeavyVehicleAccessPreviewStyle(
    val red: Int,
    val green: Int,
    val blue: Int,
    val dashArray: List<Double>
)

private fun HeavyVehicleAccessRelaxation.previewStyle(): HeavyVehicleAccessPreviewStyle =
    when (this) {
        // ROUTABLE_SNAP is drawn by the solid-route branch above.
        HeavyVehicleAccessRelaxation.ROUTABLE_SNAP ->
            HeavyVehicleAccessPreviewStyle(59, 129, 222, emptyList())

        HeavyVehicleAccessRelaxation.ACCESS_ONLY ->
            HeavyVehicleAccessPreviewStyle(255, 183, 0, listOf(1.4, 1.1))

        HeavyVehicleAccessRelaxation.WEIGHT_RELAXED ->
            HeavyVehicleAccessPreviewStyle(255, 122, 0, listOf(2.2, 1.0))

        HeavyVehicleAccessRelaxation.WEIGHT_AND_LENGTH_RELAXED ->
            HeavyVehicleAccessPreviewStyle(229, 57, 53, listOf(0.8, 0.8))
    }

private fun VisibleRegion.toRouteLabelBounds(): RouteLabelBounds {
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

    return RouteLabelBounds(
        north = latitudes.max(),
        south = latitudes.min(),
        east = longitudes.max(),
        west = longitudes.min()
    )
}

private fun VisibleRegion.routeLabelMinDistanceMeters(
    screenWidthDp: Dp,
    screenHeightDp: Dp
): Double? {
    val screenWidth = screenWidthDp.value.takeIf { it > 0f }?.toDouble() ?: return null
    val screenHeight = screenHeightDp.value.takeIf { it > 0f }?.toDouble() ?: return null
    val horizontalMetersPerDp = routeLabelHorizontalMeters() / screenWidth
    val verticalMetersPerDp = routeLabelVerticalMeters() / screenHeight
    val metersPerDp = listOf(horizontalMetersPerDp, verticalMetersPerDp)
        .filter { it > 0.0 && it.isFinite() }
        .minOrNull()
        ?: return null

    return ROUTE_LABEL_MIN_SCREEN_DISTANCE_DP.value * metersPerDp
}

private fun VisibleRegion.routeLabelHorizontalMeters(): Double {
    val topWidth = farLeft.toLatLng().fastDistanceTo(farRight.toLatLng())
    val bottomWidth = nearLeft.toLatLng().fastDistanceTo(nearRight.toLatLng())
    return (topWidth + bottomWidth) / 2.0
}

private fun VisibleRegion.routeLabelVerticalMeters(): Double {
    val leftHeight = farLeft.toLatLng().fastDistanceTo(nearLeft.toLatLng())
    val rightHeight = farRight.toLatLng().fastDistanceTo(nearRight.toLatLng())
    return (leftHeight + rightHeight) / 2.0
}

private fun Position.toLatLng(): LatLng = LatLng(latitude, longitude)

private val ROUTE_LABEL_MIN_SCREEN_DISTANCE_DP = 56.dp

@Composable
private fun TransitLayer(
    currentTransitItinerary: Itinerary?,
    highlightedTransitLegIndex: Int?
) {
    val itinerary = currentTransitItinerary ?: return
    val transferColor = MaterialTheme.colorScheme.outline
    itinerary.legs.forEachIndexed { legIndex, leg ->
        if (legIndex == highlightedTransitLegIndex) return@forEachIndexed
        key(legIndex) {
            TransitLegLayers(
                legIndex = legIndex,
                leg = leg,
                highlightedTransitLegIndex = highlightedTransitLegIndex,
                transferColor = transferColor
            )
        }
    }
    HighlightedTransitLegLayer(
        itinerary = itinerary,
        highlightedTransitLegIndex = highlightedTransitLegIndex,
        transferColor = transferColor
    )
}

@Composable
private fun TransitLegLayers(
    legIndex: Int,
    leg: Leg,
    highlightedTransitLegIndex: Int?,
    transferColor: Color
) {
    val positions = rememberTransitLegPositions(leg)
    if (positions.isEmpty()) return

    TransitLineLayerPair(
        idPrefix = "transit_leg_$legIndex",
        source = rememberTransitLegSource(positions),
        style = leg.transitLineStyle(
            highlightedTransitLegIndex = highlightedTransitLegIndex,
            transferColor = transferColor
        )
    )
}

@Composable
private fun HighlightedTransitLegLayer(
    itinerary: Itinerary,
    highlightedTransitLegIndex: Int?,
    transferColor: Color
) {
    val leg = highlightedTransitLegIndex?.let { itinerary.legs.getOrNull(it) } ?: return
    val positions = rememberTransitLegPositions(leg)
    if (positions.isEmpty()) return

    TransitLineLayerPair(
        idPrefix = "transit_leg_highlight",
        source = rememberTransitLegSource(positions),
        style = leg.highlightTransitLineStyle(transferColor)
    )
}

@Composable
private fun rememberTransitLegPositions(leg: Leg): List<Position> {
    val geometry = leg.legGeometry
    return remember(geometry?.points, geometry?.precision) {
        leg.decodeTransitPositions()
    }
}

@Composable
private fun rememberTransitLegSource(positions: List<Position>): GeoJsonSource {
    val data = remember(positions) {
        val feature = Feature(geometry = LineString(positions), properties = null)
        GeoJsonData.JsonString(Json.encodeToString(FeatureCollection(features = listOf(feature))))
    }
    return rememberGeoJsonSource(data)
}

@Composable
private fun TransitLineLayerPair(
    idPrefix: String,
    source: GeoJsonSource,
    style: TransitLineStyle
) {
    LineLayer(
        id = "${idPrefix}_casing",
        source = source,
        color = style.casingColor.toMapLibreRgbColor(),
        dasharray = style.dasharray(),
        width = const(style.casingWidth),
        opacity = const(style.casingOpacity),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )

    LineLayer(
        id = idPrefix,
        source = source,
        color = style.color.toMapLibreRgbColor(),
        dasharray = style.dasharray(),
        width = const(style.width),
        opacity = const(style.opacity),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round),
    )
}

private data class TransitLineStyle(
    val color: Color,
    val casingColor: Color,
    val width: Dp,
    val casingWidth: Dp,
    val opacity: Float,
    val casingOpacity: Float,
    val dotted: Boolean
)

private fun Leg.transitLineStyle(
    highlightedTransitLegIndex: Int?,
    transferColor: Color
): TransitLineStyle {
    val lineWidth = if (mode.isTransferLeg()) 4.dp else 6.dp
    val opacity = if (highlightedTransitLegIndex == null) 1f else 0.32f

    return TransitLineStyle(
        color = transitColor(transferColor),
        casingColor = transitColor(transferColor).darkenForCasing(),
        width = lineWidth,
        casingWidth = lineWidth + 2.dp,
        opacity = opacity,
        casingOpacity = opacity * 0.8f,
        dotted = mode == Mode.WALK
    )
}

private fun Leg.highlightTransitLineStyle(transferColor: Color): TransitLineStyle {
    val lineWidth = if (mode.isTransferLeg()) 8.dp else 10.dp
    val color = transitColor(transferColor)
    return TransitLineStyle(
        color = color,
        casingColor = color.darkenForCasing(),
        width = lineWidth,
        casingWidth = lineWidth + 4.dp,
        opacity = 1f,
        casingOpacity = 0.9f,
        dotted = mode == Mode.WALK
    )
}

private fun Leg.decodeTransitPositions(): List<Position> {
    val geometry = legGeometry ?: return emptyList()
    return runCatching {
        PolylineUtils.decodePolyline(
            encoded = geometry.points,
            precision = geometry.precision
        )
    }.getOrDefault(emptyList())
}

private fun Leg.transitColor(transferColor: Color): Color {
    if (mode.isTransferLeg()) return transferColor
    return routeColor
        ?.let(::parseRouteColor)
        ?: mode.defaultTransitModeColor()
}

private fun Color.darkenForCasing(): Color {
    return Color(red = red * 0.5f, green = green * 0.5f, blue = blue * 0.5f, alpha = alpha)
}

private fun Color.toMapLibreRgbColor() = rgbColor(
    const((red * 255).toInt()),
    const((green * 255).toInt()),
    const((blue * 255).toInt())
)

private fun TransitLineStyle.dasharray() = if (dotted) {
    const(listOf(0.01, 2.0))
} else {
    nil()
}

@Composable
private fun PinsLayer(pinFeatures: List<Feature<Point, Map<String, JsonElement>>>, useDarkTheme: Boolean) {
    SymbolLayer(
        id = "map_pins",
        source = rememberGeoJsonSource(GeoJsonData.JsonString(Json.encodeToString(FeatureCollection(features = pinFeatures)))),
        iconAllowOverlap = const(true),
        iconAnchor = const(SymbolAnchor.Bottom),
        iconImage = image(
            if (useDarkTheme) {
                painterResource(drawable.map_pin_dark)
            } else {
                painterResource(drawable.map_pin_light)
            }
        ),
    )
}

private fun Mode.isTransferLeg(): Boolean {
    return when (this) {
        Mode.WALK,
        Mode.BIKE,
        Mode.RENTAL,
        Mode.CAR,
        Mode.CAR_PARKING,
        Mode.CAR_DROPOFF -> true

        else -> false
    }
}

@Composable
private fun MapControls(
    cameraState: CameraState,
    mapViewModel: MapViewModel,
    fabInsets: PaddingValues,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit,
    appPreferences: AppPreferenceRepository,
    context: Context
) {
    val isLocating by mapViewModel.isLocating.collectAsState()
    val hasPendingLocationRequest by mapViewModel.hasPendingLocationRequest.collectAsState()
    val showZoomButtons by appPreferences.showZoomFabs.collectAsState(true)
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100000f)
            .padding(fabInsets)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = dimensionResource(dimen.padding_minor))
        ) {
            DisappearingCompassButton(
                cameraState,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = dimensionResource(dimen.padding_minor) / 2),
            )
            if (showZoomButtons) {
                FloatingActionButton(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = dimensionResource(dimen.padding_minor)),
                    onClick = {
                        coroutineScope.launch {
                            cameraState.animateTo(
                                cameraState.position.copy(
                                    zoom = min(
                                        22.0, cameraState.position.zoom + 1
                                    )
                                ),
                                duration = appPreferences.animationSpeedDurationValue,
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        painter = painterResource(drawable.zoom_in),
                        contentDescription = stringResource(string.zoom_in)
                    )
                }

                FloatingActionButton(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = dimensionResource(dimen.padding_minor)),
                    onClick = {
                        coroutineScope.launch {
                            cameraState.animateTo(
                                cameraState.position.copy(
                                    zoom = max(
                                        0.0, cameraState.position.zoom - 1
                                    )
                                ),
                                duration = appPreferences.animationSpeedDurationValue / 2,
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        painter = painterResource(drawable.zoom_out),
                        contentDescription = stringResource(string.zoom_out)
                    )
                }
            }
            FloatingActionButton(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = dimensionResource(dimen.padding_minor)),
                onClick = {
                    // Request location permissions if we don't have them
                    if (!hasLocationPermission) {
                        mapViewModel.markLocationRequestPending()
                        onRequestLocationPermission()
                    } else {
                        // Also fetch a single location and animate camera to it
                        coroutineScope.launch {
                            mapViewModel.fetchLocationAndCreateCameraPosition(context)
                                ?.let { position ->
                                    cameraState.animateTo(
                                        position,
                                        duration = appPreferences.animationSpeedDurationValue / 2
                                    )
                                }
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                if (isLocating || hasPendingLocationRequest) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        painter = painterResource(drawable.my_location),
                        contentDescription = stringResource(string.locate_me_content_description)
                    )
                }
            }
        }
    }
}
