/*
 *     Cardinal Maps / Kapijuja
 *     Copyright (C) 2026 Cardinal Maps Authors
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package earth.maps.cardinal.routing

import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.Route

/**
 * A final connector between the end of the primary route and the road point associated with the
 * requested destination. ROUTABLE_SNAP means the original strict profile can drive the connector
 * and it is drawn normally. Other levels are cautionary fallbacks that required relaxed access.
 */
data class HeavyVehicleAccessApproach(
    val route: Route,
    val relaxation: HeavyVehicleAccessRelaxation,
    /** Exact coordinate requested by the user, before Valhalla snaps it to a road edge. */
    val requestedDestination: GeographicCoordinate? = null
)

enum class HeavyVehicleAccessRelaxation {
    /**
     * No access rule was relaxed. A second strict route only bridges a different endpoint snap
     * chosen by Valhalla for an off-road/building/parking destination.
     */
    ROUTABLE_SNAP,

    /** Ignore mode-specific access tags only; keep length/width/height/weight. */
    ACCESS_ONLY,

    /** Bus-only extreme fallback: ignore access and weight; keep length/width/height. */
    WEIGHT_RELAXED,

    /** Bus-only last-resort fallback: ignore access, weight and length; keep width/height. */
    WEIGHT_AND_LENGTH_RELAXED
}


val HeavyVehicleAccessRelaxation.isCautionary: Boolean
    get() = this != HeavyVehicleAccessRelaxation.ROUTABLE_SNAP
