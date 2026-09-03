/*
 *     Cardinal Maps
 *     Copyright (C) 2025 Cardinal Maps Authors
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

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlin.math.roundToInt

/**
 * Base class for routing options that can be serialized to JSON for Valhalla API.
 */
abstract class RoutingOptions {
    abstract val costingType: String

    /**
     * Convert this options object to JSON string for Valhalla API.
     */
    @Suppress("unused")
    fun toValhallaOptionsJson(
        costingProfileOverride: ValhallaCostingProfile =
            ValhallaCostingProfile.fromRouteProviderProfile(costingType),
        departNow: Boolean = false
    ): String {
        val gson = GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
        val options = gson.toJsonTree(this@RoutingOptions).asJsonObject.apply {
            remove("costing_type")
            // App-only selector: it chooses coach (auto access) vs line bus (bus access).
            // It is not a Valhalla costing option and must never be sent to the API.
            remove("line_bus")
            if (this@RoutingOptions is BusRoutingOptions) {
                remove("axle_count")
            }

            // Stadia's hosted route schema represents second-based costs/penalties as i32.
            // Profile sliders remain Double in the app, but 300.0 is rejected before
            // routing (HTTP 400: expected i32). Normalize only at the HTTP API boundary.
            listOf(
                "maneuver_penalty",
                "gate_cost",
                "gate_penalty",
                "toll_booth_cost",
                "toll_booth_penalty",
                "private_access_penalty",
                "destination_only_penalty",
                "service_penalty",
                "alley_penalty",
                "low_class_penalty",
                "country_crossing_cost",
                "country_crossing_penalty",
                "ferry_cost",
                "rail_ferry_cost"
            ).forEach(::normalizeIntegerCost)
        }
        val wrapper = object {
            val alternates = 5
            val costingOptions = mapOf(
                costingProfileOverride.costingOptionsKey to options
            )
            val date_time = if (departNow) {
                mapOf("type" to 0)
            } else {
                null
            }
        }
        return gson.toJson(wrapper)
    }

    private fun JsonObject.normalizeIntegerCost(name: String) {
        val value = get(name) ?: return
        if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            addProperty(name, value.asDouble.roundToInt())
        }
    }
}

/**
 * Interface for routing options that include automobile-specific parameters.
 */
interface AutoOptions {
    val maneuverPenalty: Double?
    val gateCost: Double?
    val privateAccessPenalty: Double?
    val tollBoothCost: Double?
    val useHighways: Double?
    val useTolls: Double?
    val useLivingStreets: Double?
    val useTracks: Double?
    val excludeUnpaved: Boolean?
    val excludeCashOnlyTolls: Boolean?
    val ignoreClosures: Boolean?
    val ignoreRestrictions: Boolean?
    val ignoreOneWays: Boolean?
    val ignoreAccess: Boolean?
}

/**
 * Routing options for automobile mode.
 */
data class AutoRoutingOptions(
    override val costingType: String = COSTING_TYPE_AUTO,

    // Maneuver and access penalties
    override val maneuverPenalty: Double? = null,
    override val gateCost: Double? = null,
    override val tollBoothCost: Double? = null,
    override val privateAccessPenalty: Double? = null,

    // Road type preferences (0-1 range)
    override val useHighways: Double? = null,
    override val useTolls: Double? = null,
    override val useLivingStreets: Double? = null,
    override val useTracks: Double? = null,

    // Restriction options
    override val ignoreClosures: Boolean? = null,
    override val ignoreRestrictions: Boolean? = null,
    override val ignoreOneWays: Boolean? = null,
    override val ignoreAccess: Boolean? = null,

    // HOV options
    override val excludeUnpaved: Boolean? = null,
    override val excludeCashOnlyTolls: Boolean? = null
) : RoutingOptions(), AutoOptions {

    companion object {
        const val COSTING_TYPE_AUTO = "auto"
        const val DEFAULT_MANEUVER_PENALTY = 25.0
        const val DEFAULT_GATE_COST = 45.0
        const val DEFAULT_TOLL_BOOTH_COST = 30.0
    }
}

/**
 * Routing options for truck mode (extends auto with truck-specific parameters).
 */
data class TruckRoutingOptions(
    override val costingType: String = COSTING_TYPE_TRUCK,

    // Heavy-vehicle defaults: prefer roads where a full-size articulated truck belongs.
    override val maneuverPenalty: Double? = 45.0,
    override val gateCost: Double? = DEFAULT_GATE_COST,
    override val tollBoothCost: Double? = DEFAULT_TOLL_BOOTH_COST,
    override val privateAccessPenalty: Double? = null,
    override val useHighways: Double? = 0.8,
    override val useTolls: Double? = null,
    override val useLivingStreets: Double? = 0.0,
    override val useTracks: Double? = 0.0,
    override val ignoreClosures: Boolean? = false,
    override val ignoreRestrictions: Boolean? = false,
    override val ignoreOneWays: Boolean? = false,
    override val ignoreAccess: Boolean? = false,
    override val excludeUnpaved: Boolean? = true,
    override val excludeCashOnlyTolls: Boolean? = null,

    // Generic/low-class road penalties are native Valhalla costing options. They do not
    // hard-ban the destination street, but make residential/service detours expensive.
    val servicePenalty: Double? = 300.0,
    val serviceFactor: Double? = 5.0,
    // Keep destination/access-only streets available for a genuine last mile, but expensive.
    // This never overrides maxheight/maxwidth/maxlength/maxweight or other hard restrictions.
    val destinationOnlyPenalty: Double? = 600.0,
    val lowClassPenalty: Double? = 300.0,
    val closureFactor: Double? = 10.0,

    // EU full-size articulated truck baseline. 16.5 m is the standard articulated
    // vehicle maximum used as the built-in default; users can override every value.
    val length: Double? = 16.5, // meters
    val width: Double? = 2.5,
    val height: Double? = 4.0,
    val weight: Double? = 45.0, // metric tons, Kapijuja operational default
    val axleCount: Int? = 3,
    val hazmat: Boolean? = false,
    val useTruckRoute: Double? = 1.0 // 0-1 range, prefer hgv=designated network
) : RoutingOptions(), AutoOptions {

    companion object {
        const val COSTING_TYPE_TRUCK = "truck"
        const val DEFAULT_GATE_COST = 45.0
        const val DEFAULT_TOLL_BOOTH_COST = 30.0
    }
}

/**
 * Routing options for a driver-controlled bus/coach.
 *
 * Current Valhalla BusCost uses AutoCostingOptions, including physical vehicle
 * height, width, length and weight. lineBus is an app-side selector:
 * false = tourist coach (auto access semantics with bus dimensions),
 * true = line/service bus (Valhalla bus access semantics).
 */
data class BusRoutingOptions(
    override val costingType: String = COSTING_TYPE_BUS,

    // Full-size coach defaults. Avoid living streets/tracks and heavily penalize service
    // roads/alleys while still allowing a genuine destination last mile when unavoidable.
    override val maneuverPenalty: Double? = 45.0,
    override val gateCost: Double? = DEFAULT_GATE_COST,
    override val tollBoothCost: Double? = DEFAULT_TOLL_BOOTH_COST,
    override val privateAccessPenalty: Double? = null,
    override val useHighways: Double? = 0.8,
    override val useTolls: Double? = null,
    override val useLivingStreets: Double? = 0.0,
    override val useTracks: Double? = 0.0,
    override val ignoreClosures: Boolean? = false,
    override val ignoreRestrictions: Boolean? = false,
    override val ignoreOneWays: Boolean? = false,
    override val ignoreAccess: Boolean? = false,
    override val excludeUnpaved: Boolean? = true,
    override val excludeCashOnlyTolls: Boolean? = null,

    val servicePenalty: Double? = 300.0,
    val serviceFactor: Double? = 5.0,
    val alleyFactor: Double? = 10.0,
    // Tourist/line buses may use destination-only access for the final approach when OSM
    // explicitly permits it. Physical dimension/mass limits remain enforced.
    val destinationOnlyPenalty: Double? = 600.0,
    val closureFactor: Double? = 10.0,

    // Kapijuja tourist-coach baseline.
    val length: Double? = 13.5, // meters
    val width: Double? = 2.5,
    val height: Double? = 4.0,
    val weight: Double? = 18.0, // metric tons
    // Persisted for the profile and future toll/restriction integrations. Current Valhalla
    // auto/bus costing does not accept axle_count, so serialization removes it for BUS.
    val axleCount: Int? = 3,

    // App-only policy selector. Not serialized into Valhalla costing_options.
    val lineBus: Boolean = false
) : RoutingOptions(), AutoOptions {
    companion object {
        const val COSTING_TYPE_BUS = "bus"
        const val DEFAULT_GATE_COST = 45.0
        const val DEFAULT_TOLL_BOOTH_COST = 30.0
    }
}

/**
 * Routing options for motor scooter mode.
 */
data class MotorScooterRoutingOptions(
    override val costingType: String = "motor_scooter",

    // Basic auto options
    override val maneuverPenalty: Double? = null,
    override val gateCost: Double? = null,
    override val tollBoothCost: Double? = null,
    override val privateAccessPenalty: Double? = null,
    override val useHighways: Double? = null,
    override val useTolls: Double? = null,
    override val useLivingStreets: Double? = null,
    override val useTracks: Double? = null,
    override val ignoreClosures: Boolean? = null,
    override val ignoreRestrictions: Boolean? = null,
    override val ignoreOneWays: Boolean? = null,
    override val ignoreAccess: Boolean? = null,
    override val excludeUnpaved: Boolean? = null,
    override val excludeCashOnlyTolls: Boolean? = null,

    // Motor scooter specific
    val usePrimary: Double? = null,
    val useHills: Double? = null
) : RoutingOptions(), AutoOptions

/**
 * Routing options for motorcycle mode.
 */
data class MotorcycleRoutingOptions(
    override val costingType: String = "motorcycle",

    // Basic auto options
    override val maneuverPenalty: Double? = null,
    override val gateCost: Double? = null,
    override val tollBoothCost: Double? = null,
    override val privateAccessPenalty: Double? = null,
    override val useHighways: Double? = null,
    override val useTolls: Double? = null,
    override val useLivingStreets: Double? = null,
    override val useTracks: Double? = null,
    override val ignoreClosures: Boolean? = null,
    override val ignoreRestrictions: Boolean? = null,
    override val ignoreOneWays: Boolean? = null,
    override val ignoreAccess: Boolean? = null,
    override val excludeUnpaved: Boolean? = null,
    override val excludeCashOnlyTolls: Boolean? = null,

    // Motorcycle specific
    val useTrails: Double? = null
) : RoutingOptions(), AutoOptions

/**
 * Routing options for cycling mode.
 */
data class CyclingRoutingOptions(
    override val costingType: String = "bicycle",

    // Bicycle type
    @SerializedName("bicycle_type")
    val bicycleType: BicycleType? = null,

    // Speed and fitness
    val cyclingSpeed: Double? = null, // km/h

    // Road preferences (0-1 range)
    val useRoads: Double? = null,
    val useHills: Double? = null,

    // Surface preferences
    val avoidBadSurfaces: Double? = null
) : RoutingOptions()

/**
 * Routing options for pedestrian mode.
 */
data class PedestrianRoutingOptions(
    override val costingType: String = "pedestrian",

    val walkingSpeed: Double? = WALKING_SPEED_IN_KMH, // km/h

    // Path preferences (factors)
    val walkwayFactor: Double? = null,
    val sidewalkFactor: Double? = null,

    // Road preferences (0-1 range)
    val useLit: Double? = null,

    // Accessibility options
    val type: PedestrianType? = null
) : RoutingOptions() {
    companion object {
        const val WALKING_SPEED_IN_KMH = 4.2
    }
}
