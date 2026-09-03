package earth.maps.cardinal.ui.util

import earth.maps.cardinal.data.LatLng
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route

@RunWith(RobolectricTestRunner::class)
class AnnotationPlacerTest {

    private val annotationPlacer = AnnotationPlacer()

    @Test
    fun placeAnnotations_emptyRoutes_returnsEmptyMap() {
        val routes = emptyList<Route>()
        val result = annotationPlacer.placeAnnotations(routes)

        assertTrue("Result should be empty for empty input", result.isEmpty())
    }

    @Test
    fun placeAnnotations_singleRoute_placesAnnotationAtMidpoint() {
        // Create a simple straight route
        val route = createRoute(
            listOf(
                LatLng(37.7749, -122.4194), // San Francisco
                LatLng(37.7750, -122.4195), // ~14m north-east
                LatLng(37.7751, -122.4196)  // ~14m further north-east
            )
        )

        val result = annotationPlacer.placeAnnotations(listOf(route))

        assertEquals("Should have one placement for one route", 1, result.size)
        val placement = result[route]
        assertNotNull("Should have a placement for the route", placement)

        // The placement should be near the middle of the route
        val expectedMidpoint = LatLng(37.7750, -122.4195)
        assertEquals(
            "Latitude should be near midpoint",
            expectedMidpoint.latitude,
            placement!!.latitude,
            0.001
        )
        assertEquals(
            "Longitude should be near midpoint",
            expectedMidpoint.longitude,
            placement.longitude,
            0.001
        )
    }

    @Test
    fun placeAnnotations_twoNonOverlappingRoutes_placesBothAnnotations() {
        // Create two routes that don't overlap
        val route1 = createRoute(
            listOf(
                LatLng(37.7749, -122.4194), // San Francisco
                LatLng(37.7750, -122.4195)
            )
        )

        val route2 = createRoute(
            listOf(
                LatLng(40.7128, -74.0060), // New York
                LatLng(40.7129, -74.0061)
            )
        )

        val result = annotationPlacer.placeAnnotations(listOf(route1, route2))

        assertEquals("Should have placements for both routes", 2, result.size)
        assertNotNull("Should have placement for route 1", result[route1])
        assertNotNull("Should have placement for route 2", result[route2])

        // Verify placements are in different cities
        val placement1 = result[route1]!!
        val placement2 = result[route2]!!

        assertTrue(
            "Placement 1 should be in San Francisco area",
            placement1.latitude > 37.7 && placement1.latitude < 37.8
        )
        assertTrue(
            "Placement 2 should be in New York area",
            placement2.latitude > 40.7 && placement2.latitude < 40.8
        )
    }

    @Test
    fun placeAnnotations_overlappingRoutes_placesAnnotationsInDivergentSegments() {
        // Create two routes that share a common segment but diverge
        val commonStart = LatLng(37.7749, -122.4194)
        val commonMiddle = LatLng(37.7750, -122.4195)

        val route1End = LatLng(37.7751, -122.4196) // Continues northeast
        val route2End = LatLng(37.7751, -122.4194) // Continues east

        val route1 = createRoute(listOf(commonStart, commonMiddle, route1End))
        val route2 = createRoute(listOf(commonStart, commonMiddle, route2End))

        val result = annotationPlacer.placeAnnotations(listOf(route1, route2))

        assertEquals("Should have placements for both routes", 2, result.size)

        val placement1 = result[route1]!!
        val placement2 = result[route2]!!

        // Both placements should be in their divergent segments (after the common point)
        assertTrue(
            "Route 1 placement should be after divergence",
            placement1.latitude >= commonMiddle.latitude
        )
        assertTrue(
            "Route 2 placement should be after divergence",
            placement2.latitude >= commonMiddle.latitude
        )

        // They should be in different locations
        assertTrue(
            "Placements should be different",
            placement1 != placement2
        )
    }

    @Test
    fun placeAnnotations_fullyOverlappingRoutes_returnsEmptyMap() {
        // Create two identical routes that completely overlap
        val route1 = createRoute(
            listOf(
                LatLng(37.7749, -122.4194),
                LatLng(37.7750, -122.4195),
                LatLng(37.7751, -122.4196)
            )
        )

        val route2 = createRoute(
            listOf(
                LatLng(37.7749, -122.4194),
                LatLng(37.7750, -122.4195),
                LatLng(37.7751, -122.4196)
            )
        )

        val result = annotationPlacer.placeAnnotations(listOf(route1, route2))

        assertTrue("Should have no placements for fully overlapping routes", result.isEmpty())
    }

    @Test
    fun placeAnnotations_routeWithMultipleDivergentSegments_choosesLongestSegment() {
        // Create a route with two divergent segments of different lengths
        val route = createRoute(
            listOf(
                LatLng(37.7749, -122.4194), // Start
                LatLng(37.7750, -122.4195), // Short segment start
                LatLng(37.7751, -122.4196), // Short segment end
                LatLng(37.7752, -122.4197), // Overlap point (simulated overlap with another route)
                LatLng(37.7753, -122.4198), // Long segment start
                LatLng(37.7755, -122.4200), // Long segment end
                LatLng(37.7756, -122.4201)  // End
            )
        )

        // Create a second route that overlaps only at one point to create divergent segments
        val overlappingRoute = createRoute(
            listOf(
                LatLng(37.7752, -122.4197) // Only overlaps at one point
            )
        )

        val result = annotationPlacer.placeAnnotations(listOf(route, overlappingRoute))

        assertEquals("Should have one placement for the main route", 1, result.size)
        val placement = result[route]!!

        // The placement should be in the longer segment (the one with more points)
        assertTrue(
            "Placement should be in the longer divergent segment",
            placement.latitude >= 37.7753
        )
    }

    @Test
    fun placeAnnotations_routeWithSinglePoint_placesAnnotation() {
        // Create a route with just one point
        val route = createRoute(listOf(LatLng(37.7749, -122.4194)))

        val result = annotationPlacer.placeAnnotations(listOf(route))

        assertEquals("Should have one placement", 1, result.size)
        val placement = result[route]!!

        assertEquals(
            "Placement should be at the single point",
            LatLng(37.7749, -122.4194), placement
        )
    }

    @Test
    fun placeAnnotations_complexRealWorldScenario_handlesCorrectly() {
        // Simulate a realistic scenario with multiple route options
        val startPoint = LatLng(37.7749, -122.4194) // San Francisco

        // Route 1: Goes east then north, then west.
        val route1 = createRoute(
            listOf(
                LatLng(37.0, -122.0), // Starting point (common)
                LatLng(37.5, -122.0), // Common point (route2's midpoint)
                LatLng(37.5, -121.75), // East
                LatLng(37.5, -121.5), // East
                LatLng(37.75, -121.25), // Northeast
                LatLng(38.0, -121.0),  // Continue northeast
                LatLng(38.0, -121.0),  // North
                LatLng(38.0, -122.0),  // West
            )
        )

        // Route 2: Direct path north.
        val route2 = createRoute(
            listOf(
                LatLng(37.0, -122.0), // Starting point (common)
                LatLng(37.5, -122.0),  // North (midpoint of this route)
                LatLng(37.65, -122.0),  // North (begin divergent segment)
                LatLng(37.75, -122.0),  // North
                LatLng(37.85, -122.0),  // North
                LatLng(38.0, -122.0),  // North (common endpoint)
            )
        )

        val result = annotationPlacer.placeAnnotations(listOf(route1, route2))

        assertEquals("Should have placements for all routes", 2, result.size)

        // Get specific placements for each route
        val placement1 = result[route1]
        val placement2 = result[route2]

        assertNotNull("Route 1 should have placement", placement1)
        assertNotNull("Route 2 should have placement", placement2)

        // Route 1: Should be placed in its unique northern segment (after going north)
        // The algorithm should place it at the midpoint of the longest divergent segment
        assertEquals(
            "Route 1 placement should be in the east",
            LatLng(37.5, -121.5), placement1
        )

        // Route 2: Should be placed in its unique eastern segment (after going east)
        assertEquals(
            "Route 2 placement should be in its northern half's midpoint",
            LatLng(37.75, -122.0), placement2
        )
    }

    /**
     * Helper method to create a mock Route object for testing.
     * Since Route is from the uniffi.ferrostar package, we use mockk to create it.
     */
    private fun createRoute(points: List<LatLng>): Route {
        // Convert LatLng points to GeographicCoordinate
        val geometry = points.map {
            GeographicCoordinate(it.latitude, it.longitude)
        }

        // Create a mock Route using mockk
        val mockRoute = mockk<Route>()

        // Mock the geometry property to return our test points
        every { mockRoute.geometry } returns geometry

        return mockRoute
    }
}
