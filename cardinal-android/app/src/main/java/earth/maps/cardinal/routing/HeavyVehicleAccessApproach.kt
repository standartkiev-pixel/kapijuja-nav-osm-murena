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

import uniffi.ferrostar.Route

/**
 * A short, explicitly cautionary final approach that is not part of the strict heavy-vehicle
 * route. The relaxation level is kept with the geometry so preview and turn-by-turn UI can make
 * progressively riskier fallbacks visually distinct.
 */
data class HeavyVehicleAccessApproach(
    val route: Route,
    val relaxation: HeavyVehicleAccessRelaxation
)

enum class HeavyVehicleAccessRelaxation {
    /** Ignore mode-specific access tags only; keep length/width/height/weight. */
    ACCESS_ONLY,

    /** Bus-only extreme fallback: ignore access and weight; keep length/width/height. */
    WEIGHT_RELAXED,

    /** Bus-only last-resort fallback: ignore access, weight and length; keep width/height. */
    WEIGHT_AND_LENGTH_RELAXED
}
