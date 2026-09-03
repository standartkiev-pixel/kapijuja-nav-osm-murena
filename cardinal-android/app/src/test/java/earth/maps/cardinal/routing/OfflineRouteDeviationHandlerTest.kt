package earth.maps.cardinal.routing

import com.stadiamaps.ferrostar.core.CorrectiveAction
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointKind

class OfflineRouteDeviationHandlerTest {

    @Test
    fun `offline route deviation does nothing`() {
        val action = correctiveActionForConnectivity(
            isInternetConnected = false,
            remainingWaypoints = listOf(waypoint())
        )

        assertSame(CorrectiveAction.DoNothing, action)
    }

    @Test
    fun `online route deviation requests new routes`() {
        val waypoint = waypoint()
        val action = correctiveActionForConnectivity(
            isInternetConnected = true,
            remainingWaypoints = listOf(waypoint)
        )

        assertTrue(action is CorrectiveAction.GetNewRoutes)
        assertSame(waypoint, (action as CorrectiveAction.GetNewRoutes).waypoints.single())
    }

    private fun waypoint() = Waypoint(
        coordinate = GeographicCoordinate(19.253742, 72.972018),
        kind = WaypointKind.BREAK
    )
}
