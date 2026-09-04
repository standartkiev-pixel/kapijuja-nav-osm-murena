package earth.maps.cardinal.routing

import earth.maps.cardinal.data.RoutingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ValhallaCostingProfileTest {

    @Test
    fun `traffic auto profiles resolve to typed auto traffic family`() {
        assertAutoTrafficProfile("auto_traffic")
        assertAutoTrafficProfile(TrafficEtaCalibration.AUTO_TRAFFIC_PROFILE)
        assertAutoTrafficProfile("auto_traffic_experimental")
    }

    @Test
    fun `traffic truck profiles resolve to typed truck traffic family`() {
        assertTruckTrafficProfile("truck_traffic")
        assertTruckTrafficProfile("truck_traffic_premium")
    }

    @Test
    fun `traffic bus profiles resolve to typed bus traffic family`() {
        assertBusTrafficProfile("bus_traffic")
        assertBusTrafficProfile("bus_traffic_premium")
        assertBusTrafficProfile("bus_traffic_experimental")
    }

    @Test
    fun `standard profiles resolve to typed profiles`() {
        assertSame(
            ValhallaCostingProfile.Auto,
            ValhallaCostingProfile.fromRouteProviderProfile("auto")
        )
        assertSame(
            ValhallaCostingProfile.Truck,
            ValhallaCostingProfile.fromRouteProviderProfile("truck")
        )
        assertSame(
            ValhallaCostingProfile.Bus,
            ValhallaCostingProfile.fromRouteProviderProfile("bus")
        )
        assertSame(
            ValhallaCostingProfile.Bicycle,
            ValhallaCostingProfile.fromRouteProviderProfile("bicycle")
        )
        assertSame(
            ValhallaCostingProfile.Pedestrian,
            ValhallaCostingProfile.fromRouteProviderProfile("pedestrian")
        )
    }

    @Test
    fun `auto truck and bus are traffic-capable modes`() {
        assertTrue(RoutingMode.AUTO.supportsTraffic())
        assertTrue(RoutingMode.TRUCK.supportsTraffic())
        assertTrue(RoutingMode.BUS.supportsTraffic())
        assertFalse(RoutingMode.BICYCLE.supportsTraffic())
        assertFalse(RoutingMode.PEDESTRIAN.supportsTraffic())
    }

    @Test
    fun `truck traffic preserves truck costing options key`() {
        val profile = RoutingMode.TRUCK.valhallaCostingProfile(
            useTraffic = true,
            routingOptions = TruckRoutingOptions(width = 2.55, height = 4.0, weight = 18.0)
        )

        assertTrue(profile is ValhallaCostingProfile.TruckTraffic)
        assertEquals("truck_traffic_premium", profile.routeProviderProfile)
        assertEquals("truck", profile.costingOptionsKey)
    }

    @Test
    fun `default bus uses native bus traffic profile`() {
        val options = BusRoutingOptions()
        val profile = RoutingMode.BUS.valhallaCostingProfile(
            useTraffic = true,
            routingOptions = options
        )

        assertTrue(options.lineBus)
        assertTrue(profile is ValhallaCostingProfile.BusTraffic)
        assertEquals("bus_traffic_premium", profile.routeProviderProfile)
        assertEquals("bus", profile.costingOptionsKey)
    }

    @Test
    fun `native line bus traffic uses bus traffic profile`() {
        val profile = RoutingMode.BUS.valhallaCostingProfile(
            useTraffic = true,
            routingOptions = BusRoutingOptions(width = 2.55, height = 3.8, weight = 18.0, lineBus = true)
        )

        assertTrue(profile is ValhallaCostingProfile.BusTraffic)
        assertEquals("bus_traffic_premium", profile.routeProviderProfile)
        assertEquals("bus", profile.costingOptionsKey)
    }

    @Test
    fun `tourist coach traffic uses auto traffic profile while retaining coach options`() {
        val profile = RoutingMode.BUS.valhallaCostingProfile(
            useTraffic = true,
            routingOptions = BusRoutingOptions(width = 2.55, height = 3.8, weight = 18.0, lineBus = false)
        )

        assertTrue(profile is ValhallaCostingProfile.AutoTraffic)
        assertEquals("auto_traffic_premium", profile.routeProviderProfile)
        assertEquals("auto", profile.costingOptionsKey)
    }

    @Test
    fun `unknown profile resolves to custom profile`() {
        assertCustomProfile("custom_profile")
        assertCustomProfile("auto_trafficjam")
        assertCustomProfile("truck_trafficjam")
        assertCustomProfile("bus_trafficjam")
    }

    private fun assertAutoTrafficProfile(routeProviderProfile: String) {
        val profile = ValhallaCostingProfile.fromRouteProviderProfile(routeProviderProfile)

        assertTrue(profile is ValhallaCostingProfile.AutoTraffic)
        assertEquals(routeProviderProfile, profile.routeProviderProfile)
        assertEquals(AutoRoutingOptions.COSTING_TYPE_AUTO, profile.costingOptionsKey)
        assertEquals(AutoRoutingOptions.COSTING_TYPE_AUTO, profile.safeLogProfileClass)
        assertTrue(profile.usesTraffic)
    }

    private fun assertTruckTrafficProfile(routeProviderProfile: String) {
        val profile = ValhallaCostingProfile.fromRouteProviderProfile(routeProviderProfile)

        assertTrue(profile is ValhallaCostingProfile.TruckTraffic)
        assertEquals(routeProviderProfile, profile.routeProviderProfile)
        assertEquals(TruckRoutingOptions.COSTING_TYPE_TRUCK, profile.costingOptionsKey)
        assertEquals(TruckRoutingOptions.COSTING_TYPE_TRUCK, profile.safeLogProfileClass)
        assertTrue(profile.usesTraffic)
    }

    private fun assertBusTrafficProfile(routeProviderProfile: String) {
        val profile = ValhallaCostingProfile.fromRouteProviderProfile(routeProviderProfile)

        assertTrue(profile is ValhallaCostingProfile.BusTraffic)
        assertEquals(routeProviderProfile, profile.routeProviderProfile)
        assertEquals(BusRoutingOptions.COSTING_TYPE_BUS, profile.costingOptionsKey)
        assertEquals(BusRoutingOptions.COSTING_TYPE_BUS, profile.safeLogProfileClass)
        assertTrue(profile.usesTraffic)
    }

    private fun assertCustomProfile(routeProviderProfile: String) {
        val profile = ValhallaCostingProfile.fromRouteProviderProfile(routeProviderProfile)

        assertTrue(profile is ValhallaCostingProfile.Custom)
        assertEquals(routeProviderProfile, profile.routeProviderProfile)
        assertEquals(routeProviderProfile, profile.costingOptionsKey)
        assertEquals("custom", profile.safeLogProfileClass)
        assertFalse(profile.usesTraffic)
    }
}
