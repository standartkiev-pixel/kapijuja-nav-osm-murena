package earth.maps.cardinal.routing

import earth.maps.cardinal.data.RoutingMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingOptionsTrafficTest {

    @Test
    fun `auto mode uses premium traffic profile when traffic is enabled`() {
        val profile = RoutingMode.AUTO.valhallaCostingProfile(useTraffic = true)

        assertTrue(profile is ValhallaCostingProfile.AutoTraffic)
        assertEquals(TrafficEtaCalibration.AUTO_TRAFFIC_PROFILE, profile.routeProviderProfile)
        assertEquals("auto", profile.costingOptionsKey)
    }

    @Test
    fun `truck mode uses premium traffic profile when traffic is enabled`() {
        val profile = RoutingMode.TRUCK.valhallaCostingProfile(useTraffic = true)

        assertTrue(profile is ValhallaCostingProfile.TruckTraffic)
        assertEquals("truck_traffic_premium", profile.routeProviderProfile)
        assertEquals("truck", profile.costingOptionsKey)
    }

    @Test
    fun `non traffic-supported modes keep normal Valhalla profile`() {
        assertEquals("bicycle", RoutingMode.BICYCLE.valhallaProfile(useTraffic = true))
        assertFalse(RoutingMode.BICYCLE.supportsTraffic())
    }

    @Test
    fun `traffic options JSON keys costing options by base profile and departs now`() {
        val json = Json.parseToJsonElement(
            AutoRoutingOptions(useTolls = 0.0).toValhallaOptionsJson(
                costingProfileOverride = ValhallaCostingProfile.fromRouteProviderProfile(
                    TrafficEtaCalibration.AUTO_TRAFFIC_PROFILE
                ),
                departNow = true
            )
        ).jsonObject

        val costingOptions = json.getValue("costing_options").jsonObject
        assertTrue(costingOptions.containsKey("auto"))
        assertFalse(costingOptions.containsKey(TrafficEtaCalibration.AUTO_TRAFFIC_PROFILE))
        val autoOptions = costingOptions.getValue("auto").jsonObject
        assertEquals(0.0, autoOptions.getValue("use_tolls").jsonPrimitive.double, 0.0)
        assertFalse(autoOptions.containsKey("costing_type"))
        assertEquals(5, json.getValue("alternates").jsonPrimitive.int)
        assertEquals(0, json.getValue("date_time").jsonObject.getValue("type").jsonPrimitive.int)
    }

    @Test
    fun `fallback auto options JSON keeps toll options under auto profile`() {
        val json = Json.parseToJsonElement(
            AutoRoutingOptions(useTolls = 0.0).toValhallaOptionsJson(
                costingProfileOverride = ValhallaCostingProfile.Auto,
                departNow = false
            )
        ).jsonObject

        val autoOptions = json.getValue("costing_options")
            .jsonObject
            .getValue("auto")
            .jsonObject

        assertEquals(0.0, autoOptions.getValue("use_tolls").jsonPrimitive.double, 0.0)
        assertFalse(autoOptions.containsKey("costing_type"))
        assertFalse(json.containsKey("date_time"))
    }
}
