package earth.maps.cardinal.routing

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.ferrostar.BoundingBox
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep

class TrafficEtaCalibrationTest {

    @Test
    fun `premium auto traffic profile applies conservative correction`() {
        val factor = TrafficEtaCalibration.factorForProfile(
            profile = ValhallaCostingProfile.AutoTraffic.Premium,
            trafficAvailable = true
        )

        assertEquals(1.15, factor, 0.0)
        assertEquals(4_140, TrafficEtaCalibration.correctedDurationSecondsInt(3_600.0, factor))
    }

    @Test
    fun `non premium or unavailable traffic keeps raw duration`() {
        assertEquals(
            1.0,
            TrafficEtaCalibration.factorForProfile(
                ValhallaCostingProfile.Auto,
                trafficAvailable = false
            ),
            0.0
        )
        assertEquals(
            1.0,
            TrafficEtaCalibration.factorForProfile(
                ValhallaCostingProfile.TruckTraffic.Standard,
                trafficAvailable = true
            ),
            0.0
        )
    }

    @Test
    fun `route duration correction uses step duration sum`() {
        val route = route(durationSeconds = 600.0)

        assertEquals(690, TrafficEtaCalibration.correctedRouteDurationSecondsInt(route, 1.15))
    }

    private fun route(durationSeconds: Double): Route {
        val geometry = listOf(
            GeographicCoordinate(0.0, 0.0),
            GeographicCoordinate(0.0, 0.1)
        )
        return Route(
            geometry = geometry,
            bbox = BoundingBox(geometry.first(), geometry.last()),
            distance = 100.0,
            waypoints = emptyList(),
            steps = listOf(
                RouteStep(
                    geometry = geometry,
                    distance = 100.0,
                    duration = durationSeconds,
                    roadName = null,
                    exits = emptyList(),
                    instruction = "Continue",
                    visualInstructions = emptyList(),
                    spokenInstructions = emptyList(),
                    annotations = emptyList(),
                    incidents = emptyList()
                )
            )
        )
    }
}
