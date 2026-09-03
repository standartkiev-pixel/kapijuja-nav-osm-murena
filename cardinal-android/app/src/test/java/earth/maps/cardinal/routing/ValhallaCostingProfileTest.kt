package earth.maps.cardinal.routing

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
            ValhallaCostingProfile.Bicycle,
            ValhallaCostingProfile.fromRouteProviderProfile("bicycle")
        )
        assertSame(
            ValhallaCostingProfile.Pedestrian,
            ValhallaCostingProfile.fromRouteProviderProfile("pedestrian")
        )
    }

    @Test
    fun `unknown profile resolves to custom profile`() {
        assertCustomProfile("custom_profile")
        assertCustomProfile("auto_trafficjam")
        assertCustomProfile("truck_trafficjam")
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

    private fun assertCustomProfile(routeProviderProfile: String) {
        val profile = ValhallaCostingProfile.fromRouteProviderProfile(routeProviderProfile)

        assertTrue(profile is ValhallaCostingProfile.Custom)
        assertEquals(routeProviderProfile, profile.routeProviderProfile)
        assertEquals(routeProviderProfile, profile.costingOptionsKey)
        assertEquals("custom", profile.safeLogProfileClass)
        assertFalse(profile.usesTraffic)
    }
}
