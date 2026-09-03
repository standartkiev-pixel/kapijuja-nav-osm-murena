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
import earth.maps.cardinal.data.PolylineUtils
import earth.maps.cardinal.transit.Itinerary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import uniffi.ferrostar.Route

internal class TransitMapOverlayCoordinator(
    private val sink: TransitMapOverlaySink
) {
    fun ownsOverlay(route: CardinalRoute): Boolean {
        return route is CardinalRoute.Directions || route is CardinalRoute.TransitItineraryDetail
    }

    fun enterDirectionsTransitMode(itinerary: Itinerary?) {
        refreshDirectionsTransitItinerary(itinerary)
        clearRouteOverlay()
    }

    fun refreshDirectionsTransitItinerary(itinerary: Itinerary?) {
        sink.setTransitOverlay(itinerary = itinerary, highlightedLegIndex = null)
    }

    private fun clearRouteOverlay() {
        sink.setRouteOverlay(
            route = null,
            allRoutes = emptyList(),
            trafficAvailable = false,
            etaCorrectionFactor = 1.0
        )
    }

    fun enterDetailMode(itinerary: Itinerary) {
        sink.setTransitOverlay(
            itinerary = itinerary,
            highlightedLegIndex = itinerary.firstDrawableLegIndex()
        )
        clearRouteOverlay()
        sink.clearMapPins()
    }

    fun clearOverlay() {
        sink.setTransitOverlay(itinerary = null, highlightedLegIndex = null)
    }

    suspend fun fitItinerary(
        itinerary: Itinerary?,
        padding: PaddingValues,
        duration: kotlin.time.Duration
    ) {
        val boundingBox = itinerary
            ?.decodedLegPositions()
            ?.let(PolylineUtils::calculateBoundingBox)
            ?: return

        sink.animateCamera(
            boundingBox = boundingBox.toGeoJsonBoundingBox(),
            padding = padding,
            duration = duration
        )
    }

    suspend fun focusLeg(
        itinerary: Itinerary,
        legIndex: Int,
        padding: PaddingValues,
        duration: kotlin.time.Duration,
        beforeCameraAnimation: suspend () -> Unit = {}
    ) {
        val boundingBox = itinerary.legs.getOrNull(legIndex)
            ?.legGeometry
            ?.let { geometry ->
                runCatching {
                    PolylineUtils.decodePolyline(
                        geometry.points,
                        geometry.precision
                    )
                }.getOrDefault(emptyList())
            }
            ?.let(PolylineUtils::calculateBoundingBox)
            ?: return

        sink.setTransitOverlay(itinerary = itinerary, highlightedLegIndex = legIndex)
        beforeCameraAnimation()
        sink.animateCamera(
            boundingBox = boundingBox.toGeoJsonBoundingBox(),
            padding = padding,
            duration = duration
        )
    }
}

internal interface TransitMapOverlaySink {
    fun setTransitOverlay(itinerary: Itinerary?, highlightedLegIndex: Int?)

    fun setRouteOverlay(
        route: Route?,
        allRoutes: List<Route>,
        trafficAvailable: Boolean,
        etaCorrectionFactor: Double
    )

    fun clearMapPins()

    suspend fun animateCamera(
        boundingBox: BoundingBox,
        padding: PaddingValues,
        duration: kotlin.time.Duration
    )
}

internal class AppContentTransitMapOverlaySink(
    private val state: AppContentState
) : TransitMapOverlaySink {
    override fun setTransitOverlay(itinerary: Itinerary?, highlightedLegIndex: Int?) {
        state.currentTransitItinerary = itinerary
        state.highlightedTransitLegIndex = highlightedLegIndex
    }

    override fun setRouteOverlay(
        route: Route?,
        allRoutes: List<Route>,
        trafficAvailable: Boolean,
        etaCorrectionFactor: Double
    ) {
        state.currentRoute = route
        state.allRoutes = allRoutes
        state.trafficAvailable = trafficAvailable
        state.etaCorrectionFactor = etaCorrectionFactor
    }

    override fun clearMapPins() {
        state.mapPins.clear()
    }

    override suspend fun animateCamera(
        boundingBox: BoundingBox,
        padding: PaddingValues,
        duration: kotlin.time.Duration
    ) {
        state.cameraState.animateTo(
            boundingBox = boundingBox,
            padding = padding,
            duration = duration
        )
    }
}

internal class TransitCameraFocusJob {
    private var job: Job? = null

    fun launchLatest(scope: CoroutineScope, block: suspend () -> Unit) {
        cancel()
        job = scope.launch {
            block()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

private fun Itinerary.decodedLegPositions(): List<Position> {
    return legs.flatMap { leg ->
        leg.legGeometry?.let { geometry ->
            runCatching {
                PolylineUtils.decodePolyline(geometry.points, geometry.precision)
            }.getOrDefault(emptyList())
        }.orEmpty()
    }
}

private fun Itinerary.firstDrawableLegIndex(): Int? {
    return legs.indexOfFirst { leg ->
        leg.legGeometry?.let { geometry ->
            runCatching {
                PolylineUtils.decodePolyline(geometry.points, geometry.precision)
            }.getOrDefault(emptyList())
        }.orEmpty().isNotEmpty()
    }.takeIf { index -> index >= 0 }
}
