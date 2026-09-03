/*
 *     Cardinal Maps
 *     Copyright (C) 2026 Cardinal Maps Authors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package earth.maps.cardinal.routing

sealed class ValhallaCostingProfile(
    val routeProviderProfile: String,
    val costingOptionsKey: String,
    val safeLogProfileClass: String,
    val usesTraffic: Boolean = false
) {
    object Auto : ValhallaCostingProfile(
        routeProviderProfile = AutoRoutingOptions.COSTING_TYPE_AUTO,
        costingOptionsKey = AutoRoutingOptions.COSTING_TYPE_AUTO,
        safeLogProfileClass = AutoRoutingOptions.COSTING_TYPE_AUTO
    )

    class AutoTraffic private constructor(routeProviderProfile: String) : ValhallaCostingProfile(
        routeProviderProfile = routeProviderProfile,
        costingOptionsKey = AutoRoutingOptions.COSTING_TYPE_AUTO,
        safeLogProfileClass = AutoRoutingOptions.COSTING_TYPE_AUTO,
        usesTraffic = true
    ) {
        companion object {
            val Standard = AutoTraffic(ProfileFamilies.AUTO_TRAFFIC_PROFILE_PREFIX)
            val Premium = AutoTraffic(TrafficEtaCalibration.AUTO_TRAFFIC_PROFILE)

            fun fromRouteProviderProfile(routeProviderProfile: String): AutoTraffic =
                AutoTraffic(routeProviderProfile)
        }
    }

    object Truck : ValhallaCostingProfile(
        routeProviderProfile = TruckRoutingOptions.COSTING_TYPE_TRUCK,
        costingOptionsKey = TruckRoutingOptions.COSTING_TYPE_TRUCK,
        safeLogProfileClass = TruckRoutingOptions.COSTING_TYPE_TRUCK
    )

    class TruckTraffic private constructor(routeProviderProfile: String) : ValhallaCostingProfile(
        routeProviderProfile = routeProviderProfile,
        costingOptionsKey = TruckRoutingOptions.COSTING_TYPE_TRUCK,
        safeLogProfileClass = TruckRoutingOptions.COSTING_TYPE_TRUCK,
        usesTraffic = true
    ) {
        companion object {
            val Standard = TruckTraffic(ProfileFamilies.TRUCK_TRAFFIC_PROFILE_PREFIX)

            fun fromRouteProviderProfile(routeProviderProfile: String): TruckTraffic =
                TruckTraffic(routeProviderProfile)
        }
    }

    object MotorScooter : ValhallaCostingProfile(
        routeProviderProfile = "motor_scooter",
        costingOptionsKey = "motor_scooter",
        safeLogProfileClass = "motor_scooter"
    )

    object Motorcycle : ValhallaCostingProfile(
        routeProviderProfile = "motorcycle",
        costingOptionsKey = "motorcycle",
        safeLogProfileClass = "motorcycle"
    )

    object Bicycle : ValhallaCostingProfile(
        routeProviderProfile = "bicycle",
        costingOptionsKey = "bicycle",
        safeLogProfileClass = "bicycle"
    )

    object Pedestrian : ValhallaCostingProfile(
        routeProviderProfile = "pedestrian",
        costingOptionsKey = "pedestrian",
        safeLogProfileClass = "pedestrian"
    )

    class Custom private constructor(routeProviderProfile: String) : ValhallaCostingProfile(
        routeProviderProfile = routeProviderProfile,
        costingOptionsKey = routeProviderProfile,
        safeLogProfileClass = "custom"
    ) {
        companion object {
            fun fromRouteProviderProfile(routeProviderProfile: String): Custom =
                Custom(routeProviderProfile)
        }
    }

    companion object {
        fun fromRouteProviderProfile(routeProviderProfile: String): ValhallaCostingProfile = when {
            routeProviderProfile == Auto.routeProviderProfile -> Auto
            routeProviderProfile == Truck.routeProviderProfile -> Truck
            routeProviderProfile == MotorScooter.routeProviderProfile -> MotorScooter
            routeProviderProfile == Motorcycle.routeProviderProfile -> Motorcycle
            routeProviderProfile == Bicycle.routeProviderProfile -> Bicycle
            routeProviderProfile == Pedestrian.routeProviderProfile -> Pedestrian
            ProfileFamilies.isAutoTrafficProfile(routeProviderProfile) ->
                AutoTraffic.fromRouteProviderProfile(routeProviderProfile)

            ProfileFamilies.isTruckTrafficProfile(routeProviderProfile) ->
                TruckTraffic.fromRouteProviderProfile(routeProviderProfile)

            else -> Custom.fromRouteProviderProfile(routeProviderProfile)
        }
    }

    private object ProfileFamilies {
        const val AUTO_TRAFFIC_PROFILE_PREFIX = "auto_traffic"
        const val TRUCK_TRAFFIC_PROFILE_PREFIX = "truck_traffic"

        fun isAutoTrafficProfile(routeProviderProfile: String): Boolean =
            routeProviderProfile.matchesProfileFamily(AUTO_TRAFFIC_PROFILE_PREFIX)

        fun isTruckTrafficProfile(routeProviderProfile: String): Boolean =
            routeProviderProfile.matchesProfileFamily(TRUCK_TRAFFIC_PROFILE_PREFIX)

        private fun String.matchesProfileFamily(prefix: String): Boolean =
            this == prefix || startsWith("${prefix}_")
    }
}
