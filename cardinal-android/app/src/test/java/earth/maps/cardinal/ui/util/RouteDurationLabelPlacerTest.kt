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

package earth.maps.cardinal.ui.util

import earth.maps.cardinal.data.LatLng
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route

class RouteDurationLabelPlacerTest {

    private val placer = RouteDurationLabelPlacer(minLabelDistanceMeters = 180.0)

    @Test
    fun place_usesPreferredPositionsWhenTheyDoNotOverlap() {
        val route1 = createRoute(LatLng(0.0, 0.0), LatLng(0.0, 0.02))
        val route2 = createRoute(LatLng(1.0, 1.0), LatLng(1.0, 1.02))
        val route1Position = LatLng(0.0, 0.01)
        val route2Position = LatLng(1.0, 1.01)

        val labels = placer.place(
            routes = listOf(route1, route2),
            preferredPositions = mapOf(
                route1 to route1Position,
                route2 to route2Position
            )
        )

        assertEquals(2, labels.size)
        assertEquals(route2Position, labels[0].position)
        assertEquals(route1Position, labels[1].position)
    }

    @Test
    fun place_fallsBackToRouteMidpointWhenNoPreferredPositionExists() {
        val route = createRoute(LatLng(0.0, 0.0), LatLng(0.0, 0.02))

        val labels = placer.place(
            routes = listOf(route),
            preferredPositions = emptyMap()
        )

        assertEquals(1, labels.size)
        assertEquals(0, labels[0].routeIndex)
        assertEquals(0.0, labels[0].position.latitude, 0.0001)
        assertEquals(0.01, labels[0].position.longitude, 0.0001)
    }

    @Test
    fun place_replacesOffscreenPreferredPositionWithVisibleRouteCandidate() {
        val route = createRoute(LatLng(0.0, 0.0), LatLng(0.0, 0.04))
        val visibleBounds = RouteLabelBounds(
            north = 0.01,
            south = -0.01,
            east = 0.015,
            west = 0.006
        )

        val labels = placer.place(
            routes = listOf(route),
            preferredPositions = mapOf(route to LatLng(5.0, 5.0)),
            visibleBounds = visibleBounds
        )

        assertEquals(1, labels.size)
        assertTrue(visibleBounds.contains(labels[0].position))
    }

    @Test
    fun place_movesLaterLabelWhenPreferredPositionsOverlap() {
        val route1 = createRoute(LatLng(0.0, 0.0), LatLng(0.0, 0.02))
        val route2 = createRoute(LatLng(0.0, 0.0), LatLng(0.0, 0.02))
        val sharedPosition = LatLng(0.0, 0.01)

        val labels = placer.place(
            routes = listOf(route1, route2),
            preferredPositions = mapOf(
                route1 to sharedPosition,
                route2 to sharedPosition
            )
        )

        assertEquals(2, labels.size)
        assertEquals(sharedPosition, labels[0].position)
        assertTrue(
            "Second label should move away from the occupied preferred position",
            labels[0].position.fastDistanceTo(labels[1].position) >= 180.0
        )
    }

    @Test
    fun place_allowsNearbyPreferredPositionsWhenScreenDerivedSpacingIsSmall() {
        val labelPlacer = RouteDurationLabelPlacer(minLabelDistanceMeters = 50.0)
        val route1 = createRoute(LatLng(0.0, 0.0), LatLng(0.0, 0.02))
        val route2 = createRoute(LatLng(0.0, 0.0), LatLng(0.0, 0.02))
        val route1Position = LatLng(0.0, 0.0100)
        val route2Position = LatLng(0.0, 0.0108)

        val labels = labelPlacer.place(
            routes = listOf(route1, route2),
            preferredPositions = mapOf(
                route1 to route1Position,
                route2 to route2Position
            )
        )

        assertEquals(2, labels.size)
        assertEquals(route2Position, labels[0].position)
        assertEquals(route1Position, labels[1].position)
    }

    @Test
    fun place_prioritizesVisibleFallbackCandidateWhenPreferredPositionsOverlap() {
        val route1 = createRoute(LatLng(0.0, 0.0), LatLng(0.0, 0.04))
        val route2 = createRoute(LatLng(0.0, 0.0), LatLng(0.0, 0.04))
        val sharedPosition = LatLng(0.0, 0.01)
        val visibleBounds = RouteLabelBounds(
            north = 0.01,
            south = -0.01,
            east = 0.015,
            west = 0.006
        )

        val labels = placer.place(
            routes = listOf(route1, route2),
            preferredPositions = mapOf(
                route1 to sharedPosition,
                route2 to sharedPosition
            ),
            visibleBounds = visibleBounds
        )

        assertEquals(2, labels.size)
        assertEquals(sharedPosition, labels[0].position)
        assertTrue(visibleBounds.contains(labels[1].position))
        assertTrue(
            "Second visible label should still avoid the occupied preferred position",
            labels[0].position.fastDistanceTo(labels[1].position) >= 180.0
        )
    }

    private fun createRoute(vararg points: LatLng): Route {
        val routeGeometry = points.map { point ->
            GeographicCoordinate(point.latitude, point.longitude)
        }
        return mockk<Route> {
            every { geometry } returns routeGeometry
        }
    }
}
