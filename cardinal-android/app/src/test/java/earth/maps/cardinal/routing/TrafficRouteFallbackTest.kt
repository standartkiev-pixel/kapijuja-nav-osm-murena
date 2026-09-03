package earth.maps.cardinal.routing

import com.stadiamaps.ferrostar.core.InvalidStatusCodeException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import uniffi.ferrostar.BoundingBox
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.ParsingException
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep

class TrafficRouteFallbackTest {

    @Test
    fun `UniFFI invalid status from traffic route retries without traffic`() = runTest {
        var trafficEnabled = false
        var fetchCount = 0
        val fallbackRoute = route()

        val result = runTrafficFallbackRouteRequest(
            supportsTraffic = true,
            isTrafficEnabled = { trafficEnabled },
            setTrafficEnabled = { trafficEnabled = it },
            fetchRoutes = {
                fetchCount += 1
                if (fetchCount == 1) {
                    throw ParsingException.InvalidStatusCode(
                        code = "400",
                        description = "Unsupported costing profile"
                    )
                }
                listOf(fallbackRoute)
            }
        )

        assertEquals(listOf(fallbackRoute), result.routes)
        assertFalse(result.trafficAvailable)
        assertFalse(trafficEnabled)
        assertEquals(2, fetchCount)
    }

    @Test
    fun `paid traffic entitlement failure retries without traffic`() = runTest {
        listOf(402, 403).forEach { status ->
            var trafficEnabled = false
            var fetchCount = 0
            val fallbackRoute = route()

            val result = runTrafficFallbackRouteRequest(
                supportsTraffic = true,
                isTrafficEnabled = { trafficEnabled },
                setTrafficEnabled = { trafficEnabled = it },
                fetchRoutes = {
                    fetchCount += 1
                    if (fetchCount == 1) {
                        throw InvalidStatusCodeException(statusCode = status)
                    }
                    listOf(fallbackRoute)
                }
            )

            assertEquals(listOf(fallbackRoute), result.routes)
            assertFalse(result.trafficAvailable)
            assertFalse(trafficEnabled)
            assertEquals(2, fetchCount)
        }
    }

    @Test(expected = InvalidStatusCodeException::class)
    fun `network outage status does not retry without traffic`() = runTest {
        var trafficEnabled = false

        runTrafficFallbackRouteRequest(
            supportsTraffic = true,
            isTrafficEnabled = { trafficEnabled },
            setTrafficEnabled = { trafficEnabled = it },
            fetchRoutes = {
                throw InvalidStatusCodeException(statusCode = 503)
            }
        )
    }

    @Test(expected = ParsingException.InvalidStatusCode::class)
    fun `UniFFI network outage status does not retry without traffic`() = runTest {
        var trafficEnabled = false

        runTrafficFallbackRouteRequest(
            supportsTraffic = true,
            isTrafficEnabled = { trafficEnabled },
            setTrafficEnabled = { trafficEnabled = it },
            fetchRoutes = {
                throw ParsingException.InvalidStatusCode(
                    code = "503",
                    description = "No internet"
                )
            }
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `non status failures are not swallowed`() = runTest {
        runTrafficFallbackRouteRequest(
            supportsTraffic = true,
            isTrafficEnabled = { true },
            setTrafficEnabled = {},
            fetchRoutes = {
                throw IllegalStateException("Network unavailable")
            }
        )
    }

    @Test(expected = ParsingException.InvalidStatusCode::class)
    fun `status failures are not swallowed when traffic is unsupported`() = runTest {
        runTrafficFallbackRouteRequest(
            supportsTraffic = false,
            isTrafficEnabled = { false },
            setTrafficEnabled = {},
            fetchRoutes = {
                throw ParsingException.InvalidStatusCode(
                    code = "400",
                    description = "Unsupported costing profile"
                )
            }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `fallback route failure is propagated`() = runTest {
        var trafficEnabled = false
        var fetchCount = 0

        runTrafficFallbackRouteRequest(
            supportsTraffic = true,
            isTrafficEnabled = { trafficEnabled },
            setTrafficEnabled = { trafficEnabled = it },
            fetchRoutes = {
                fetchCount += 1
                if (fetchCount == 1) {
                    throw ParsingException.InvalidStatusCode(
                        code = "400",
                        description = "Unsupported costing profile"
                    )
                }
                throw IllegalArgumentException("Fallback route failed")
            }
        )
    }

    private fun route(): Route {
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
                    duration = 60.0,
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
