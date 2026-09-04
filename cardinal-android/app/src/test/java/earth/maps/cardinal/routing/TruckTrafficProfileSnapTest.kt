package earth.maps.cardinal.routing

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TruckTrafficProfileSnapTest {
    @Test
    fun `plain and traffic truck costings use truck waypoint correlation`() {
        assertTrue(isTruckCostingProfile("truck"))
        assertTrue(isTruckCostingProfile("truck_traffic"))
        assertTrue(isTruckCostingProfile("truck_traffic_premium"))
        assertTrue(isTruckCostingProfile("truck_traffic_experimental"))
    }

    @Test
    fun `non truck costings are not classified as truck`() {
        assertFalse(isTruckCostingProfile("auto"))
        assertFalse(isTruckCostingProfile("auto_traffic_premium"))
        assertFalse(isTruckCostingProfile("bus"))
        assertFalse(isTruckCostingProfile("bus_traffic_premium"))
    }

    @Test
    fun `bus and bus traffic use heavy vehicle waypoint correlation`() {
        assertTrue(isBusCostingProfile("bus"))
        assertTrue(isBusCostingProfile("bus_traffic"))
        assertTrue(isBusCostingProfile("bus_traffic_premium"))
    }

    @Test
    fun `coach auto with dimensions receives heavy vehicle waypoint radius`() {
        val request = Json.parseToJsonElement(
            """{"costing":"auto_traffic_premium","costing_options":{"auto":{"length":13.5,"width":2.5,"height":4.0,"weight":18.0}}}"""
        ).jsonObject

        assertEquals(
            600,
            heavyVehicleWaypointRadiusMeters(
                costing = "auto_traffic_premium",
                requestRoot = request
            )
        )
    }

    @Test
    fun `normal passenger auto does not receive heavy vehicle waypoint radius`() {
        val request = Json.parseToJsonElement(
            """{"costing":"auto_traffic_premium","costing_options":{"auto":{"use_tolls":0.5}}}"""
        ).jsonObject

        assertNull(
            heavyVehicleWaypointRadiusMeters(
                costing = "auto_traffic_premium",
                requestRoot = request
            )
        )
    }

    @Test
    fun `truck stays at 100 metres while bus uses 600 metres`() {
        val emptyRequest = Json.parseToJsonElement(
            """{"costing_options":{}}"""
        ).jsonObject

        assertEquals(100, heavyVehicleWaypointRadiusMeters("truck", emptyRequest))
        assertEquals(600, heavyVehicleWaypointRadiusMeters("bus", emptyRequest))
        assertEquals(600, heavyVehicleWaypointRadiusMeters("bus_traffic_premium", emptyRequest))
    }

    @Test
    fun `dashed coach fallback keeps exact requested endpoint without snap radius`() {
        val request = Json.parseToJsonElement(
            """{"costing":"auto","costing_options":{"auto":{"width":2.5,"height":4.0,"ignore_access":true}}}"""
        ).jsonObject

        assertNull(
            heavyVehicleWaypointRadiusMeters(
                costing = "auto",
                requestRoot = request
            )
        )
    }

}
