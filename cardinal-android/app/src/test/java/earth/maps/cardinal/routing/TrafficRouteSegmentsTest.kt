package earth.maps.cardinal.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.BoundingBox
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep

class TrafficRouteSegmentsTest {

    @Test
    fun `build creates one traffic segment per adjacent geometry pair`() {
        val route = routeWithStep(
            geometry = listOf(
                GeographicCoordinate(0.0, 0.0),
                GeographicCoordinate(0.0, 0.1),
                GeographicCoordinate(0.0, 0.2),
                GeographicCoordinate(0.0, 0.3),
            ),
            annotations = listOf(
                annotation(speed = 25.0),
                annotation(speed = 15.0),
                annotation(speed = 2.0),
            )
        )

        val segments = TrafficRouteSegments.build(route)

        assertEquals(3, segments.size)
        assertEquals(TrafficLevel.FREE, segments[0].level)
        assertEquals(TrafficLevel.MODERATE, segments[1].level)
        assertEquals(TrafficLevel.SEVERE, segments[2].level)
        assertTrue(TrafficRouteSegments.trafficAvailable(route))
    }

    @Test
    fun `build marks invalid or missing annotations as unknown`() {
        val route = routeWithStep(
            geometry = listOf(
                GeographicCoordinate(0.0, 0.0),
                GeographicCoordinate(0.0, 0.1),
                GeographicCoordinate(0.0, 0.2),
            ),
            annotations = listOf("{bad json")
        )

        val segments = TrafficRouteSegments.build(route)

        assertEquals(2, segments.size)
        assertEquals(TrafficLevel.UNKNOWN, segments[0].level)
        assertEquals(TrafficLevel.UNKNOWN, segments[1].level)
        assertFalse(TrafficRouteSegments.trafficAvailable(route))
    }

    @Test
    fun `duration and distance are used when speed is missing`() {
        val route = routeWithStep(
            geometry = listOf(
                GeographicCoordinate(0.0, 0.0),
                GeographicCoordinate(0.0, 0.1),
            ),
            annotations = listOf(
                """
                    {
                        "maxspeed": { "speed": 60.0, "unit": "mph" },
                        "speed": null,
                        "distance": 100.0,
                        "duration": 20.0
                    }
                """.trimIndent()
            )
        )

        assertEquals(TrafficLevel.SEVERE, TrafficRouteSegments.build(route).single().level)
    }

    @Test
    fun `build creates traffic segments from remaining route steps`() {
        val firstStep = routeStep(
            geometry = listOf(
                GeographicCoordinate(0.0, 0.0),
                GeographicCoordinate(0.0, 0.1),
            ),
            annotations = listOf(annotation(speed = 25.0))
        )
        val secondStep = routeStep(
            geometry = listOf(
                GeographicCoordinate(0.0, 0.1),
                GeographicCoordinate(0.0, 0.2),
            ),
            annotations = listOf(annotation(speed = 2.0))
        )

        val segments = TrafficRouteSegments.build(listOf(firstStep, secondStep))

        assertEquals(2, segments.size)
        assertEquals(TrafficLevel.FREE, segments[0].level)
        assertEquals(TrafficLevel.SEVERE, segments[1].level)
        assertTrue(TrafficRouteSegments.trafficAvailable(listOf(firstStep, secondStep)))
    }

    private fun annotation(speed: Double): String =
        """
            {
                "maxspeed": { "speed": 60.0, "unit": "mph" },
                "speed": $speed,
                "distance": 100.0,
                "duration": 5.0
            }
        """.trimIndent()

    private fun routeWithStep(
        geometry: List<GeographicCoordinate>,
        annotations: List<String>
    ): Route = Route(
        geometry = geometry,
        bbox = BoundingBox(geometry.first(), geometry.last()),
        distance = 100.0,
        waypoints = emptyList(),
        steps = listOf(routeStep(geometry, annotations))
    )

    private fun routeStep(
        geometry: List<GeographicCoordinate>,
        annotations: List<String>
    ): RouteStep = RouteStep(
        geometry = geometry,
        distance = 100.0,
        duration = 10.0,
        roadName = null,
        exits = emptyList(),
        instruction = "Continue",
        visualInstructions = emptyList(),
        spokenInstructions = emptyList(),
        annotations = annotations,
        incidents = emptyList()
    )
}
