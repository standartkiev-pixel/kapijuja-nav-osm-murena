package earth.maps.cardinal.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Guards the operational heavy-vehicle baseline and the Valhalla API boundary.
class HeavyVehicleDefaultsTest {
    @Test
    fun `truck defaults describe full size articulated vehicle and avoid low class roads`() {
        val options = TruckRoutingOptions()
        assertEquals(16.5, options.length!!, 0.0)
        assertEquals(2.5, options.width!!, 0.0)
        assertEquals(4.0, options.height!!, 0.0)
        assertEquals(45.0, options.weight!!, 0.0)
        assertEquals(3, options.axleCount)
        assertEquals(1.0, options.useTruckRoute!!, 0.0)
        assertEquals(0.0, options.useLivingStreets!!, 0.0)
        assertTrue(options.excludeUnpaved == true)
        assertEquals(300.0, options.lowClassPenalty!!, 0.0)
        assertEquals(600.0, options.destinationOnlyPenalty!!, 0.0)
    }

    @Test
    fun `coach defaults describe three axle 13 point 5 metre bus`() {
        val options = BusRoutingOptions()
        assertEquals(13.5, options.length!!, 0.0)
        assertEquals(2.5, options.width!!, 0.0)
        assertEquals(4.0, options.height!!, 0.0)
        assertEquals(18.0, options.weight!!, 0.0)
        assertEquals(3, options.axleCount)
        assertFalse(options.lineBus)
        assertEquals(0.0, options.useLivingStreets!!, 0.0)
        assertTrue(options.excludeUnpaved == true)
        assertEquals(600.0, options.destinationOnlyPenalty!!, 0.0)
    }

    @Test
    fun `bus axle count stays app side while physical dimensions reach Valhalla`() {
        val json = BusRoutingOptions().toValhallaOptionsJson(
            costingProfileOverride = ValhallaCostingProfile.Auto
        )
        assertFalse(json.contains("axle_count"))
        assertTrue(json.contains("\"length\":13.5"))
        assertTrue(json.contains("\"width\":2.5"))
        assertTrue(json.contains("\"height\":4.0"))
        assertTrue(json.contains("\"weight\":18.0"))
        assertTrue(json.contains("\"service_penalty\":300"))
        assertTrue(json.contains("\"destination_only_penalty\":600"))
        assertFalse(json.contains("\"service_penalty\":300.0"))
        assertTrue(json.contains("\"maneuver_penalty\":45"))
    }

    @Test
    fun `truck low class and truck route preferences reach Valhalla`() {
        val json = TruckRoutingOptions().toValhallaOptionsJson(
            costingProfileOverride = ValhallaCostingProfile.Truck
        )
        assertTrue(json.contains("\"low_class_penalty\":300"))
        assertTrue(json.contains("\"destination_only_penalty\":600"))
        assertFalse(json.contains("\"low_class_penalty\":300.0"))
        assertTrue(json.contains("\"use_truck_route\":1.0"))
        assertTrue(json.contains("\"axle_count\":3"))
        assertTrue(json.contains("\"closure_factor\":10.0"))
    }

    @Test
    fun `truck access approach ignores access tags but keeps physical restrictions enabled`() {
        val access = TruckRoutingOptions().toHeavyVehicleAccessOptions()
            ?: error("truck access options missing")
        val json = access.toValhallaOptionsJson(
            costingProfileOverride = ValhallaCostingProfile.Auto
        )

        assertTrue(json.contains("\"ignore_access\":true"))
        assertTrue(json.contains("\"ignore_restrictions\":false"))
        assertTrue(json.contains("\"ignore_one_ways\":false"))
        assertTrue(json.contains("\"ignore_closures\":false"))
        assertTrue(json.contains("\"length\":16.5"))
        assertTrue(json.contains("\"width\":2.5"))
        assertTrue(json.contains("\"height\":4.0"))
        assertTrue(json.contains("\"weight\":45.0"))
        assertTrue(json.contains("\"costing_options\":{\"auto\""))
    }

    @Test
    fun `bus access approach preserves coach dimensions while using car access semantics`() {
        val access = BusRoutingOptions().toHeavyVehicleAccessOptions()
            ?: error("bus access options missing")
        val json = access.toValhallaOptionsJson(
            costingProfileOverride = ValhallaCostingProfile.Auto
        )

        assertTrue(json.contains("\"ignore_access\":true"))
        assertTrue(json.contains("\"ignore_restrictions\":false"))
        assertTrue(json.contains("\"length\":13.5"))
        assertTrue(json.contains("\"height\":4.0"))
        assertTrue(json.contains("\"weight\":18.0"))
        assertFalse(json.contains("line_bus"))
        assertFalse(json.contains("axle_count"))
    }


    @Test
    fun `bus weight relaxed fallback still preserves length width and height`() {
        val base = BusRoutingOptions().toHeavyVehicleAccessOptions()
            ?: error("bus access options missing")
        val json = base.copy(weight = 0.0).toValhallaOptionsJson(
            costingProfileOverride = ValhallaCostingProfile.Auto
        )

        assertTrue(json.contains("\"weight\":0.0"))
        assertTrue(json.contains("\"length\":13.5"))
        assertTrue(json.contains("\"width\":2.5"))
        assertTrue(json.contains("\"height\":4.0"))
        assertTrue(json.contains("\"ignore_access\":true"))
        assertTrue(json.contains("\"ignore_restrictions\":false"))
    }

    @Test
    fun `bus last resort may relax weight and length but never width or height`() {
        val base = BusRoutingOptions().toHeavyVehicleAccessOptions()
            ?: error("bus access options missing")
        val json = base.copy(weight = 0.0, length = 0.0).toValhallaOptionsJson(
            costingProfileOverride = ValhallaCostingProfile.Auto
        )

        assertTrue(json.contains("\"weight\":0.0"))
        assertTrue(json.contains("\"length\":0.0"))
        assertTrue(json.contains("\"width\":2.5"))
        assertTrue(json.contains("\"height\":4.0"))
        assertTrue(json.contains("\"ignore_access\":true"))
        assertTrue(json.contains("\"ignore_restrictions\":false"))
    }

}
