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
import android.location.Location
import android.util.Log
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.times
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import earth.maps.cardinal.data.Address
import earth.maps.cardinal.data.CountryCoordinateResolver
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.LocationRepository
import earth.maps.cardinal.data.OrientationRepository
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.ViewportPreferences
import earth.maps.cardinal.data.ViewportRepository
import earth.maps.cardinal.data.room.SavedPlace
import earth.maps.cardinal.data.room.SavedPlaceDao
import earth.maps.cardinal.geocoding.GeocodingService
import earth.maps.cardinal.geocoding.OfflineGeocodingService
import earth.maps.cardinal.ui.util.AnnotationPlacer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraState
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point
import org.maplibre.spatialk.geojson.Position
import uniffi.ferrostar.Route
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

internal val MAP_POI_CLICKABLE_LAYER_IDS = setOf(
    "map_pins",
    "user_favorites",
    "poi_parking",
    "poi_z14",
    "poi_z15",
    "poi_z16",
    "poi_transit"
)

/**
 * ViewModel responsible for handling map-related functionality including location services.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val viewportPreferences: ViewportPreferences,
    private val viewportRepository: ViewportRepository,
    private val locationRepository: LocationRepository,
    private val orientationRepository: OrientationRepository,
    private val geocodingService: GeocodingService,
    private val offlineGeocodingService: OfflineGeocodingService,
    private val placeDao: SavedPlaceDao,
    private val annotationPlacer: AnnotationPlacer,
) : ViewModel() {

    private companion object {
        const val TAG = "MapViewModel"
        const val REVERSE_GEOCODE_TIMEOUT_MILLIS = 2_000L
    }

    // State flows for UI components - delegate to repository
    val isLocating: StateFlow<Boolean> = locationRepository.isLocating

    private val _hasPendingLocationRequest = MutableStateFlow(false)
    val hasPendingLocationRequest: StateFlow<Boolean> = _hasPendingLocationRequest

    val locationFlow: StateFlow<Location?> = locationRepository.locationFlow

    val heading: StateFlow<Float?> = orientationRepository.azimuth

    // Permission tracking
    private val previousPermissionState = AtomicBoolean(false)

    var peekHeight: Dp = 0.dp
    var screenHeight: Dp = 0.dp
    var screenWidth: Dp = 0.dp

    val savedPlacesFlow: Flow<FeatureCollection<Point, Map<String, JsonElement>>> = placeDao.getAllPlacesAsFlow().map { placeList ->
        FeatureCollection(placeList.map { createFeatureFromSavedPlace(it) })
    }

    /**
     * Creates a Feature from a Place with proper JSON escaping.
     */
    fun createFeatureFromPlace(place: Place): Feature<Point, Map<String, JsonElement>> {
        val properties = mutableMapOf(
            "name" to escapeJsonString(place.name),
            "description" to escapeJsonString(place.description),
        )

        place.id?.let { properties["id"] = escapeJsonString(it) }
        place.address?.houseNumber?.let { properties["addr:housenumber"] = escapeJsonString(it) }
        place.address?.road?.let { properties["addr:street"] = escapeJsonString(it) }
        place.address?.city?.let { properties["addr:city"] = escapeJsonString(it) }
        place.address?.postcode?.let { properties["addr:postcode"] = escapeJsonString(it) }
        place.address?.state?.let { properties["addr:state"] = escapeJsonString(it) }
        place.address?.country?.let { properties["addr:country"] = escapeJsonString(it) }
        place.address?.countryCode?.let { properties["country_code"] = escapeJsonString(it) }
        place.transitStopId?.let { properties["transit_stop_id"] = escapeJsonString(it) }

        return Feature(
            geometry = Point(
                Position(
                    latitude = place.latLng.latitude,
                    longitude = place.latLng.longitude
                )
            ),
            properties = properties
        )
    }

    /**
     * Creates a Feature from a SavedPlace with proper JSON escaping.
     */
    private fun createFeatureFromSavedPlace(place: SavedPlace): Feature<Point, Map<String, JsonElement>> {
        val name = place.customName ?: place.name
        val description = place.customDescription ?: place.type

        val properties = mutableMapOf(
            "saved_poi_id" to escapeJsonString(place.id),
            "name" to escapeJsonString(name),
            "description" to escapeJsonString(description)
        )

        place.houseNumber?.let { properties["addr:housenumber"] = escapeJsonString(it) }
        place.road?.let { properties["addr:street"] = escapeJsonString(it) }
        place.city?.let { properties["addr:city"] = escapeJsonString(it) }
        place.postcode?.let { properties["addr:postcode"] = escapeJsonString(it) }
        place.state?.let { properties["addr:state"] = escapeJsonString(it) }
        place.country?.let { properties["addr:country"] = escapeJsonString(it) }
        place.countryCode?.let { properties["country_code"] = escapeJsonString(it) }
        place.transitStopId?.let { properties["transit_stop_id"] = escapeJsonString(it) }

        return Feature(
            geometry = Point(Position(latitude = place.latitude, longitude = place.longitude)),
            properties = properties
        )
    }

    private fun escapeJsonString(input: String): JsonElement {
        return Json.parseToJsonElement(
            Json.encodeToString(
                String.serializer(),
                input
            )
        )
    }

    suspend fun enrichPlaceWithReverseGeocodedCountry(place: Place): Place {
        if (place.address.hasCountryInformation()) {
            return place
        }

        val reverseAddress = reverseGeocodeCountryAddress(place)
            ?: CountryCoordinateResolver.resolve(place.latLng)
            ?: return place

        val mergedAddress = place.address.mergeCountryInformation(reverseAddress)
        return place.copy(address = mergedAddress)
    }

    private suspend fun reverseGeocodeCountryAddress(place: Place): Address? {
        return runCatching {
            withTimeoutOrNull(REVERSE_GEOCODE_TIMEOUT_MILLIS) {
                geocodingService.reverseGeocode(
                    latitude = place.latLng.latitude,
                    longitude = place.latLng.longitude
                ).firstNotNullOfOrNull { reversePlace ->
                    reversePlace.address?.takeIf { it.hasCountryInformation() }
                }
            }
        }.getOrNull()
    }

    private fun Address?.hasCountryInformation(): Boolean {
        return this?.country?.isNotBlank() == true || this?.countryCode?.isNotBlank() == true
    }

    private fun Address?.mergeCountryInformation(reverseAddress: Address): Address {
        return (this ?: Address()).copy(
            country = this?.country?.takeIf { it.isNotBlank() } ?: reverseAddress.country,
            countryCode = this?.countryCode?.takeIf { it.isNotBlank() } ?: reverseAddress.countryCode
        )
    }

    /**
     * Saves the current viewport to preferences.
     */
    fun saveViewport(cameraPosition: CameraPosition) {
        viewportPreferences.saveViewport(cameraPosition)
        // Update the viewport center for geocoding focus
        updateViewportCenter(cameraPosition)
    }

    /**
     * Updates the current viewport center for geocoding focus.
     */
    fun updateViewportCenter(cameraPosition: CameraPosition) {
        viewportRepository.updateViewportCenter(cameraPosition)
    }

    /**
     * Loads the saved viewport from preferences.
     * Returns null if no viewport has been saved.
     */
    fun loadViewport(): CameraPosition? {
        return viewportPreferences.loadViewport()
    }

    /**
     * Marks that a location request is pending due to missing permissions.
     */
    fun markLocationRequestPending() {
        _hasPendingLocationRequest.value = true
    }

    fun handleMapTap(
        cameraState: CameraState,
        dpOffset: DpOffset,
        onMapPoiClick: (Place) -> Unit,
        onMapInteraction: () -> Unit,
        onRouteAnnotationClick: ((Int) -> Unit)? = null,
    ) {
        // Check for route annotation features first
        val routeAnnotationFeatures = cameraState.projection?.queryRenderedFeatures(
            dpOffset,
            layerIds = setOf("route_annotations", "route_lines_casing", "route_lines")
        )

        val routeAnnotationFeature = routeAnnotationFeatures?.firstOrNull()
        if (routeAnnotationFeature != null) {
            // Extract route index from layer ID
            val routeIndex =
                routeAnnotationFeature.properties?.get("routeIndex")?.jsonPrimitive?.content?.toIntOrNull()

            if (routeIndex != null && onRouteAnnotationClick != null) {
                onRouteAnnotationClick(routeIndex)
                return
            }
        }

        val features: List<Feature<Geometry, Map<String, JsonElement>>>? = cameraState.projection?.queryRenderedFeatures(
            dpOffset,
            layerIds = MAP_POI_CLICKABLE_LAYER_IDS
        )?.map { feature ->
            Feature(feature.geometry, feature.properties?.toMap() ?: mapOf())
        }
        Log.d(TAG, "${features?.count()} features available at tap location")
        val filteredFeatures = features?.filter {
            it.geometry is Point
        }
        val savedFeatures = filteredFeatures?.filter { it.properties.contains("saved_poi_id") }
        val transitFeatures =
            filteredFeatures?.filter { it.properties["class"]?.jsonPrimitive?.content == "bus" }
        val namedFeatures = filteredFeatures?.filter { it.properties.contains("name") }
        val feature = savedFeatures?.firstOrNull() ?: transitFeatures?.firstOrNull()
        ?: namedFeatures?.firstOrNull() ?: filteredFeatures?.firstOrNull()
        if (feature != null) {
            val feature = convertFeatureToPlace(
                feature
            )
            feature?.let { onMapPoiClick(it) }
        } else {
            onMapInteraction()
        }
    }

    fun convertFeatureToPlace(feature: Feature<Geometry, Map<String, JsonElement>>): Place? {
        // Convert JsonElement properties to Map<String, String>
        val tags = feature.properties.mapValues { (_, value) ->
            value.jsonPrimitive.content
        }

        // Extract coordinates from geometry (assuming Point geometry)
        val point = feature.geometry
        if (point !is Point) {
            return null
        }
        val coordinates = point.coordinates
        val longitude = coordinates.longitude
        val latitude = coordinates.latitude

        // These two lines are not ideal. Ideally we'd have a less heavyweight way to format the address.
        val result =
            offlineGeocodingService.buildResult(tags, latitude = latitude, longitude = longitude)
        return locationRepository.createSearchResultPlace(result).copy(
            transitStopId = tags["transit_stop_id"],
            isTransitStop = tags.containsKey("transit_stop_id"),
            id = tags["saved_poi_id"]
        )
    }

    /**
     * Handles permission state changes and initiates location request if needed.
     *
     * @param hasPermission Current permission state
     * @param cameraState Camera state to animate to location
     * @param context Android context for location services
     */
    suspend fun handlePermissionStateChange(
        hasPermission: Boolean, cameraState: CameraState, context: Context
    ) {
        val previousState = previousPermissionState.getAndSet(hasPermission)
        // Check if permissions changed from denied to granted and we have a pending request
        if (!previousState && hasPermission && _hasPendingLocationRequest.value) {
            _hasPendingLocationRequest.value = false
            fetchLocationAndCreateCameraPosition(context)?.let { position ->
                cameraState.animateTo(position)
            }
        }
    }

    fun placeRouteAnnotations(routes: List<Route>): Map<Route, LatLng> {
        return annotationPlacer.placeAnnotations(routes)
    }

    /**
     * Fetches the current location and returns a CameraPosition to animate to.
     * Returns null if location cannot be determined.
     */
    suspend fun fetchLocationAndCreateCameraPosition(
        context: Context,
    ): CameraPosition? {
        val location = locationRepository.getCurrentLocation(context)
        return location?.let { createCameraPosition(it) }
    }

    /**
     * Creates a CameraPosition from a Location.
     */
    private fun createCameraPosition(location: Location): CameraPosition {
        return CameraPosition(
            target = Position(location.longitude, location.latitude),
            zoom = 15.0,
            padding = PaddingValues(
                start = screenWidth / 8,
                top = screenHeight / 8,
                end = screenWidth / 8,
                bottom = min(
                    3f * screenHeight / 4, peekHeight + screenHeight / 8
                )
            )
        )
    }
}
