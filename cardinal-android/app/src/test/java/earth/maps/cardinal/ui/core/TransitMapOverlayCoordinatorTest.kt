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
import androidx.compose.ui.unit.dp
import earth.maps.cardinal.transit.EncodedPolyline
import earth.maps.cardinal.transit.Itinerary
import earth.maps.cardinal.transit.Leg
import earth.maps.cardinal.transit.Mode
import earth.maps.cardinal.transit.TransitPlace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.spatialk.geojson.BoundingBox
import uniffi.ferrostar.Route
import kotlin.time.Duration

class TransitMapOverlayCoordinatorTest {

    @Test
    fun `ownsOverlay identifies routes allowed to keep transit overlay`() {
        val coordinator = TransitMapOverlayCoordinator(FakeTransitMapOverlaySink())

        assertTrue(coordinator.ownsOverlay(CardinalRoute.Directions()))
        assertTrue(coordinator.ownsOverlay(CardinalRoute.TransitItineraryDetail(itinerary())))
        assertFalse(coordinator.ownsOverlay(CardinalRoute.HomeSearch))
    }

    @Test
    fun `enterDirectionsTransitMode clears non-transit route overlay and selection`() {
        val sink = FakeTransitMapOverlaySink()
        val coordinator = TransitMapOverlayCoordinator(sink)
        val itinerary = itinerary()
        sink.setTransitOverlay(itinerary = itinerary(legGeometry = encodedPolyline), highlightedLegIndex = 0)

        coordinator.enterDirectionsTransitMode(itinerary)

        assertSame(itinerary, sink.itinerary)
        assertNull(sink.highlightedLegIndex)
        assertTrue(sink.routeOverlayCleared)
        assertFalse(sink.mapPinsCleared)
    }

    @Test
    fun `refreshDirectionsTransitItinerary resets highlight without clearing non-transit route overlay`() {
        val sink = FakeTransitMapOverlaySink()
        val coordinator = TransitMapOverlayCoordinator(sink)
        val itinerary = itinerary()
        sink.setTransitOverlay(itinerary = itinerary(legGeometry = encodedPolyline), highlightedLegIndex = 0)

        coordinator.refreshDirectionsTransitItinerary(itinerary)

        assertSame(itinerary, sink.itinerary)
        assertNull(sink.highlightedLegIndex)
        assertFalse(sink.routeOverlayCleared)
    }

    @Test
    fun `enterDetailMode clears map pins in addition to route overlay`() {
        val sink = FakeTransitMapOverlaySink()
        val coordinator = TransitMapOverlayCoordinator(sink)
        val itinerary = itinerary(legGeometry = encodedPolyline)

        coordinator.enterDetailMode(itinerary)

        assertSame(itinerary, sink.itinerary)
        assertEquals(0, sink.highlightedLegIndex)
        assertTrue(sink.routeOverlayCleared)
        assertTrue(sink.mapPinsCleared)
        assertTrue(sink.cameraAnimations.isEmpty())
    }

    @Test
    fun `focusLeg highlights leg and animates camera to leg bounds`() = runTest {
        val sink = FakeTransitMapOverlaySink()
        val coordinator = TransitMapOverlayCoordinator(sink)
        var beforeCameraAnimationCalled = false

        coordinator.focusLeg(
            itinerary = itinerary(legGeometry = encodedPolyline),
            legIndex = 0,
            padding = PaddingValues(0.dp),
            duration = Duration.ZERO,
            beforeCameraAnimation = {
                beforeCameraAnimationCalled = true
            }
        )

        assertEquals(0, sink.highlightedLegIndex)
        assertTrue(beforeCameraAnimationCalled)
        assertEquals(1, sink.cameraAnimations.size)
        sink.cameraAnimations.single().also { animation ->
            assertEquals(-126.453, animation.boundingBox.west, 0.0)
            assertEquals(-120.2, animation.boundingBox.east, 0.0)
            assertEquals(38.5, animation.boundingBox.south, 0.0)
            assertEquals(43.252, animation.boundingBox.north, 0.0)
        }
    }

    @Test
    fun `focusLeg does not change highlight when leg has no camera bounds`() = runTest {
        val sink = FakeTransitMapOverlaySink()
        val coordinator = TransitMapOverlayCoordinator(sink)
        val existingItinerary = itinerary(legGeometry = encodedPolyline)
        sink.setTransitOverlay(itinerary = existingItinerary, highlightedLegIndex = 0)

        coordinator.focusLeg(
            itinerary = itinerary(legGeometry = null),
            legIndex = 0,
            padding = PaddingValues(0.dp),
            duration = Duration.ZERO
        )

        assertSame(existingItinerary, sink.itinerary)
        assertEquals(0, sink.highlightedLegIndex)
        assertTrue(sink.cameraAnimations.isEmpty())
    }

    private class FakeTransitMapOverlaySink : TransitMapOverlaySink {
        var itinerary: Itinerary? = null
        var highlightedLegIndex: Int? = null
        var routeOverlayCleared = false
        var mapPinsCleared = false
        val cameraAnimations = mutableListOf<CameraAnimation>()

        override fun setTransitOverlay(itinerary: Itinerary?, highlightedLegIndex: Int?) {
            this.itinerary = itinerary
            this.highlightedLegIndex = highlightedLegIndex
        }

        override fun setRouteOverlay(
            route: Route?,
            allRoutes: List<Route>,
            trafficAvailable: Boolean,
            etaCorrectionFactor: Double
        ) {
            routeOverlayCleared = route == null &&
                allRoutes.isEmpty() &&
                !trafficAvailable &&
                etaCorrectionFactor == 1.0
        }

        override fun clearMapPins() {
            mapPinsCleared = true
        }

        override suspend fun animateCamera(
            boundingBox: BoundingBox,
            padding: PaddingValues,
            duration: kotlin.time.Duration
        ) {
            cameraAnimations += CameraAnimation(boundingBox = boundingBox)
        }
    }

    private data class CameraAnimation(
        val boundingBox: BoundingBox
    )

    private fun itinerary(legGeometry: EncodedPolyline? = null): Itinerary {
        return Itinerary(
            duration = 900,
            startTime = "2026-08-04T10:00:00Z",
            endTime = "2026-08-04T10:15:00Z",
            transfers = 0,
            legs = listOf(transitLeg(legGeometry))
        )
    }

    private fun transitLeg(legGeometry: EncodedPolyline?): Leg {
        return Leg(
            mode = Mode.BUS,
            fromTransitPlace = TransitPlace("From", null, 0.0, 0.0, 0.0),
            toTransitPlace = TransitPlace("To", null, 1.0, 1.0, 0.0),
            duration = 900,
            startTime = "2026-08-04T10:00:00Z",
            endTime = "2026-08-04T10:15:00Z",
            scheduledStartTime = "2026-08-04T10:00:00Z",
            scheduledEndTime = "2026-08-04T10:15:00Z",
            realTime = false,
            scheduled = true,
            legGeometry = legGeometry
        )
    }

    private companion object {
        val encodedPolyline = EncodedPolyline(
            points = "_p~iF~ps|U_ulLnnqC_mqNvxq`@",
            precision = 5
        )
    }
}

class TransitItineraryAutoFitStateTest {

    @Test
    fun shouldFit_returnsTrueForFirstIdentityOnlyUntilMarked() {
        val state = TransitItineraryAutoFitState()

        assertTrue(state.shouldFit("itinerary-a"))

        state.markFitted("itinerary-a")

        assertFalse(state.shouldFit("itinerary-a"))
    }

    @Test
    fun shouldFit_returnsTrueWhenIdentityChanges() {
        val state = TransitItineraryAutoFitState()

        state.markFitted("itinerary-a")

        assertTrue(state.shouldFit("itinerary-b"))
    }

    @Test
    fun reset_allowsSameIdentityToFitAgain() {
        val state = TransitItineraryAutoFitState()
        state.markFitted("itinerary-a")

        state.reset()

        assertTrue(state.shouldFit("itinerary-a"))
    }
}
