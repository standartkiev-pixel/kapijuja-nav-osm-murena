package earth.maps.cardinal.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.BoundingBox
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep

class TrafficRouteMonitorTest {

    @Test
    fun `same path candidate does not replace for small ETA changes`() {
        val currentGeometry = geometry(0.0, 0.1, 0.2)
        val replacement = route(currentGeometry, durationSeconds = 580.0)

        val evaluation = TrafficRouteMonitor.evaluateRoutes(
            currentRemainingGeometry = currentGeometry,
            currentDurationRemainingSeconds = 600.0,
            candidateRoutes = listOf(replacement),
            nowMillis = 1_000L,
            suppressSuggestionsUntilMillis = 0L
        )

        assertNull(evaluation.replacementRoute)
        assertNull(evaluation.suggestion)
    }

    @Test
    fun `same path candidate replaces when ETA changes meaningfully`() {
        val currentGeometry = geometry(0.0, 0.1, 0.2)
        val replacement = route(currentGeometry, durationSeconds = 500.0)

        val evaluation = TrafficRouteMonitor.evaluateRoutes(
            currentRemainingGeometry = currentGeometry,
            currentDurationRemainingSeconds = 600.0,
            candidateRoutes = listOf(replacement),
            nowMillis = 1_000L,
            suppressSuggestionsUntilMillis = 0L
        )

        assertSame(replacement, evaluation.replacementRoute)
        assertNull(evaluation.suggestion)
    }

    @Test
    fun `different route prompts when savings exceed threshold`() {
        val currentGeometry = geometry(0.0, 0.1, 0.2)
        val fasterAlternative = route(geometry(0.0, 0.4, 0.2), durationSeconds = 350.0)

        val evaluation = TrafficRouteMonitor.evaluateRoutes(
            currentRemainingGeometry = currentGeometry,
            currentDurationRemainingSeconds = 600.0,
            candidateRoutes = listOf(fasterAlternative),
            nowMillis = 1_000L,
            suppressSuggestionsUntilMillis = 0L
        )

        assertNull(evaluation.replacementRoute)
        assertNotNull(evaluation.suggestion)
        assertEquals(250, evaluation.suggestion!!.timeSavingsSeconds)
    }

    @Test
    fun `different route prompt reports calibrated savings`() {
        val currentGeometry = geometry(0.0, 0.1, 0.2)
        val fasterAlternative = route(geometry(0.0, 0.4, 0.2), durationSeconds = 350.0)

        val evaluation = TrafficRouteMonitor.evaluateRoutes(
            currentRemainingGeometry = currentGeometry,
            currentDurationRemainingSeconds = 600.0,
            candidateRoutes = listOf(fasterAlternative),
            nowMillis = 1_000L,
            suppressSuggestionsUntilMillis = 0L,
            etaCorrectionFactor = 1.15
        )

        assertNull(evaluation.replacementRoute)
        assertNotNull(evaluation.suggestion)
        assertEquals(288, evaluation.suggestion!!.timeSavingsSeconds)
    }

    @Test
    fun `different route does not prompt below threshold`() {
        val currentGeometry = geometry(0.0, 0.1, 0.2)
        val slightlyFasterAlternative = route(geometry(0.0, 0.4, 0.2), durationSeconds = 500.0)

        val evaluation = TrafficRouteMonitor.evaluateRoutes(
            currentRemainingGeometry = currentGeometry,
            currentDurationRemainingSeconds = 600.0,
            candidateRoutes = listOf(slightlyFasterAlternative),
            nowMillis = 1_000L,
            suppressSuggestionsUntilMillis = 0L
        )

        assertNull(evaluation.replacementRoute)
        assertNull(evaluation.suggestion)
    }

    @Test
    fun `dismissed prompt suppresses suggestions`() {
        val currentGeometry = geometry(0.0, 0.1, 0.2)
        val fasterAlternative = route(geometry(0.0, 0.4, 0.2), durationSeconds = 350.0)

        val evaluation = TrafficRouteMonitor.evaluateRoutes(
            currentRemainingGeometry = currentGeometry,
            currentDurationRemainingSeconds = 600.0,
            candidateRoutes = listOf(fasterAlternative),
            nowMillis = 1_000L,
            suppressSuggestionsUntilMillis = 2_000L
        )

        assertNull(evaluation.replacementRoute)
        assertNull(evaluation.suggestion)
    }

    @Test
    fun `refresh triggers by time or distance`() {
        val start = GeographicCoordinate(0.0, 0.0)
        val farAway = GeographicCoordinate(0.01, 0.0)

        assertTrue(
            TrafficRouteMonitor.shouldRefresh(
                nowMillis = TrafficRouteMonitor.REFRESH_INTERVAL_MILLIS,
                lastRefreshMillis = 0L,
                currentLocation = start,
                lastRefreshLocation = start
            )
        )
        assertTrue(
            TrafficRouteMonitor.shouldRefresh(
                nowMillis = 1_000L,
                lastRefreshMillis = 0L,
                currentLocation = farAway,
                lastRefreshLocation = start
            )
        )
        assertFalse(
            TrafficRouteMonitor.shouldRefresh(
                nowMillis = 1_000L,
                lastRefreshMillis = 0L,
                currentLocation = start,
                lastRefreshLocation = start
            )
        )
    }

    private fun geometry(vararg longitudes: Double): List<GeographicCoordinate> =
        longitudes.map { GeographicCoordinate(0.0, it) }

    private fun route(
        geometry: List<GeographicCoordinate>,
        durationSeconds: Double
    ): Route = Route(
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
