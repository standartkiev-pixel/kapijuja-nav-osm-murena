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

package earth.maps.cardinal.ui.core

import androidx.navigation3.runtime.NavKey
import com.google.gson.Gson
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.RoutingMode
import earth.maps.cardinal.transit.Itinerary
import kotlinx.serialization.Serializable

@Serializable
sealed interface CardinalRoute : NavKey {
    @Serializable
    data object HomeSearch : CardinalRoute

    @Serializable
    data object NearbyPoi : CardinalRoute

    @Serializable
    data object NearbyCategoryFilters : CardinalRoute

    @Serializable
    data object NearbyTransit : CardinalRoute

    @Serializable
    data class PlaceCard(
        val placeJson: String,
        val preserveMapZoom: Boolean = false
    ) : CardinalRoute {
        constructor(
            place: Place,
            preserveMapZoom: Boolean = false
        ) : this(RoutePayloads.toJson(place), preserveMapZoom)

        val place: Place?
            get() = RoutePayloads.fromJson(placeJson, Place::class.java)
    }

    @Serializable
    data class ManagePlaces(
        val listId: String? = null,
        val parents: List<String> = emptyList()
    ) : CardinalRoute

    @Serializable
    data object OfflineAreas : CardinalRoute

    @Serializable
    data object Settings : CardinalRoute

    @Serializable
    data object OfflineSettings : CardinalRoute

    @Serializable
    data object AccessibilitySettings : CardinalRoute

    @Serializable
    data object ThemeSettings : CardinalRoute

    @Serializable
    data object AdvancedSettings : CardinalRoute

    @Serializable
    data object RoutingProfiles : CardinalRoute

    @Serializable
    data class ProfileEditor(val profileId: String?) : CardinalRoute

    @Serializable
    data class Directions(
        val fromPlaceJson: String? = null,
        val toPlaceJson: String? = null
    ) : CardinalRoute {
        constructor(fromPlace: Place?, toPlace: Place?) : this(
            fromPlaceJson = fromPlace?.let(RoutePayloads::toJson),
            toPlaceJson = toPlace?.let(RoutePayloads::toJson)
        )

        val fromPlace: Place?
            get() = fromPlaceJson?.let { RoutePayloads.fromJson(it, Place::class.java) }

        val toPlace: Place?
            get() = toPlaceJson?.let { RoutePayloads.fromJson(it, Place::class.java) }
    }

    @Serializable
    data class TransitItineraryDetail(val itineraryJson: String) : CardinalRoute {
        constructor(itinerary: Itinerary) : this(RoutePayloads.toJson(itinerary))

        val itinerary: Itinerary?
            get() = RoutePayloads.fromJson(itineraryJson, Itinerary::class.java)
    }

    @Serializable
    data class TurnByTurnNavigation(
        val routeId: String,
        val routingModeValue: String
    ) : CardinalRoute {
        constructor(routeId: String, routingMode: RoutingMode) : this(
            routeId = routeId,
            routingModeValue = routingMode.value
        )

        val routingMode: RoutingMode
            get() = RoutingMode.entries.firstOrNull {
                it.value.equals(routingModeValue, ignoreCase = true)
            } ?: RoutingMode.AUTO
    }
}

private object RoutePayloads {
    private val gson = Gson()

    fun toJson(value: Any): String = gson.toJson(value)

    fun <T> fromJson(json: String, klass: Class<T>): T? =
        runCatching { gson.fromJson(json, klass) }.getOrNull()
}
