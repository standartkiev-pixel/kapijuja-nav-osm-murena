package earth.maps.cardinal.routing

import org.junit.Assert.assertFalse
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
    fun `non truck costings do not receive truck waypoint correlation`() {
        assertFalse(isTruckCostingProfile("auto"))
        assertFalse(isTruckCostingProfile("auto_traffic_premium"))
        assertFalse(isTruckCostingProfile("bus"))
        assertFalse(isTruckCostingProfile("bus_traffic_premium"))
    }
}
