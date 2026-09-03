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

package earth.maps.cardinal.data

import androidx.annotation.VisibleForTesting
import earth.maps.cardinal.transit.Itinerary
import earth.maps.cardinal.transit.Leg
import earth.maps.cardinal.transit.Mode
import earth.maps.cardinal.transit.PlanResponse
import earth.maps.cardinal.transit.TransitPlace
import earth.maps.cardinal.ui.directions.DirectionUiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.ExperimentalTime

data class TransitPlanState(
    val planResponse: PlanResponse? = null,
    val itineraries: List<Itinerary> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val directionError: DirectionUiError? = null,
    val selectedItineraryIndex: Int? = null
) {
    val selectedItinerary: Itinerary?
        get() = selectedItineraryIndex?.let { itineraries.getOrNull(it) }
}

class PlanStateRepository {
    private val _planState = MutableStateFlow(TransitPlanState())
    val planState: StateFlow<TransitPlanState> = _planState.asStateFlow()

    fun setLoading(isLoading: Boolean) {
        _planState.value = _planState.value.copy(isLoading = isLoading)
    }

    @OptIn(ExperimentalTime::class)
    fun setPlanResponse(planResponse: PlanResponse?) {
        val itineraries = planResponse?.allItineraries().orEmpty()
        _planState.value = _planState.value.copy(
            planResponse = planResponse,
            itineraries = itineraries,
            isLoading = false,
            error = null,
            directionError = null,
            selectedItineraryIndex = if (itineraries.isEmpty()) null else 0
        )
    }

    fun selectItinerary(index: Int) {
        if (index in _planState.value.itineraries.indices) {
            _planState.value = _planState.value.copy(selectedItineraryIndex = index)
        }
    }

    fun setError(error: String?) {
        _planState.value = _planState.value.copy(
            planResponse = null,
            itineraries = emptyList(),
            isLoading = false,
            error = error,
            directionError = null,
            selectedItineraryIndex = null
        )
    }

    fun setDirectionError(directionUiError: DirectionUiError?) {
        _planState.value = _planState.value.copy(
            planResponse = null,
            itineraries = emptyList(),
            isLoading = false,
            error = null,
            directionError = directionUiError,
            selectedItineraryIndex = null
        )
    }

    fun clear() {
        _planState.value = TransitPlanState()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun setStateForTest(state: TransitPlanState) {
        _planState.value = state
    }
}

object StableTransitItineraryIdentityPolicy {
    fun identityOf(itinerary: Itinerary): String {
        val legIdentities = itinerary.legs.mapIndexed { index, leg ->
            stableKey(index.toString(), leg.tripIdentity() ?: leg.stopTimeIdentity())
        }
        return if (legIdentities.isNotEmpty()) {
            stableKey("itinerary-legs", *legIdentities.toTypedArray())
        } else {
            itinerary.fallbackIdentity()
        }
    }

    private fun Leg.tripIdentity(): String? {
        val trip = tripId?.takeIf { id -> id.isNotBlank() } ?: return null
        return stableKey(
            "trip",
            trip,
            stopTimeIdentity()
        )
    }

    private fun Leg.stopTimeIdentity(): String {
        return stableKey(
            "stop-time",
            mode.name,
            agencyId.orEmpty(),
            routeShortName.orEmpty(),
            fromTransitPlace.stablePlaceKey(),
            toTransitPlace.stablePlaceKey(),
            scheduledStartTime,
            scheduledEndTime
        )
    }

    private fun Itinerary.fallbackIdentity(): String {
        return stableKey(
            "itinerary",
            startTime,
            endTime,
            duration.toString(),
            transfers.toString()
        )
    }

    private fun TransitPlace.stablePlaceKey(): String {
        return stopId?.takeIf { id -> id.isNotBlank() }
            ?: stableKey(name, lat.toString(), lon.toString())
    }

    private fun stableKey(vararg parts: String): String =
        parts.joinToString(separator = "|")
}
