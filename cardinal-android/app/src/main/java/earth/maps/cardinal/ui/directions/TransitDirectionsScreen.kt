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

package earth.maps.cardinal.ui.directions

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.R.plurals
import earth.maps.cardinal.R.string
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.GeoUtils
import earth.maps.cardinal.data.StableTransitItineraryIdentityPolicy
import earth.maps.cardinal.data.formatDuration
import earth.maps.cardinal.data.formatTime
import earth.maps.cardinal.data.parseRouteColor
import earth.maps.cardinal.transit.Itinerary
import earth.maps.cardinal.transit.Mode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitDirectionsScreen(
    viewModel: DirectionsViewModel,
    onItineraryClick: (Itinerary, Int) -> Unit = { _, _ -> },
    appPreferences: AppPreferenceRepository
) {
    val use24HourFormat by appPreferences.use24HourFormat.collectAsStateWithLifecycle()
    val distanceUnit by appPreferences.distanceUnit.collectAsStateWithLifecycle()
    val planStateState by viewModel.planState.collectAsStateWithLifecycle()
    val planState = planStateState // Strip delegated property status.
    when {
        planState.isLoading -> {
            Text(
                text = stringResource(string.calculating_route_in_progress),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(dimen.padding))
            )
        }

        planState.error != null -> {
            Text(
                text = stringResource(string.directions_error, planState.error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(dimen.padding))
            )
        }

        planState.directionError != null -> {
            Text(
                text = directionUiErrorMessage(planState.directionError),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(dimen.padding))
            )
        }

        planState.planResponse != null -> {
            TransitTimelineResults(
                itineraries = planState.itineraries,
                selectedItineraryIndex = planState.selectedItineraryIndex,
                onItineraryClick = onItineraryClick,
                use24HourFormat = use24HourFormat,
                distanceUnit = distanceUnit,
                modifier = Modifier.fillMaxWidth()
            )
        }

        else -> {
            // No plan calculated yet
            Text(
                text = stringResource(string.enter_start_and_end_locations_to_get_directions),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(dimen.padding))
            )
        }
    }
}

@Composable
private fun TransitTimelineResults(
    itineraries: List<Itinerary>,
    selectedItineraryIndex: Int?,
    onItineraryClick: (Itinerary, Int) -> Unit,
    use24HourFormat: Boolean,
    distanceUnit: Int,
    modifier: Modifier = Modifier
) {
    val itemKeys = remember(itineraries) {
        itineraries.stableLazyColumnKeys()
    }
    LazyColumn(modifier = modifier) {
        itemsIndexed(
            items = itineraries,
            key = { index, _ -> itemKeys[index] }
        ) { index, itinerary ->
            TransitItineraryCard(
                itinerary = itinerary,
                isSelected = selectedItineraryIndex == index,
                onItineraryClick = { onItineraryClick(itinerary, index) },
                use24HourFormat = use24HourFormat,
                distanceUnit = distanceUnit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dimensionResource(dimen.padding))
            )
        }
    }
}

private fun List<Itinerary>.stableLazyColumnKeys(): List<String> {
    val identityOccurrences = mutableMapOf<String, Int>()
    return map { itinerary ->
        val identity = StableTransitItineraryIdentityPolicy.identityOf(itinerary)
        val occurrence = identityOccurrences.getOrDefault(identity, 0)
        identityOccurrences[identity] = occurrence + 1
        if (occurrence == 0) identity else "$identity#$occurrence"
    }
}

@Composable
private fun TransitItineraryCard(
    itinerary: Itinerary,
    isSelected: Boolean,
    onItineraryClick: () -> Unit,
    use24HourFormat: Boolean,
    distanceUnit: Int,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Card(
        modifier = modifier,
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        onClick = onItineraryClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(dimen.padding))
        ) {
            // Itinerary summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dimensionResource(dimen.padding)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(string.depart, itinerary.startTime.formatTime(use24HourFormat)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(string.arrive, itinerary.endTime.formatTime(use24HourFormat)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatDuration(itinerary.duration),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = pluralStringResource(
                            plurals.transit_transfer_count,
                            itinerary.transfers,
                            itinerary.transfers
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Timeline of legs
            Column(modifier = Modifier.fillMaxWidth()) {
                itinerary.legs.forEachIndexed { index, leg ->
                    TransitLegTimelineItem(
                        leg = leg,
                        isLast = index == itinerary.legs.lastIndex,
                        distanceUnit = distanceUnit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun TransitLegTimelineItem(
    leg: earth.maps.cardinal.transit.Leg,
    isLast: Boolean,
    distanceUnit: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier, verticalAlignment = Alignment.Top
    ) {
        // Timeline indicator
        Column(
            modifier = Modifier
                .padding(end = dimensionResource(dimen.padding))
                .width(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mode icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color = leg.routeColor?.let {
                        parseRouteColor(
                            it
                        )
                    } ?: MaterialTheme.colorScheme.primary,
                        shape = androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(leg.mode.transitIcon()),
                    contentDescription = null,
                    tint = leg.routeTextColor?.let {
                        parseRouteColor(
                            it
                        )
                    } ?: MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp))
            }

            // Connection line (except for last item)
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(1.dp)
                        )
                )
            }
        }

        // Leg details
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Route name and headsign
                    val routeText = leg.routeShortName
                        ?: stringResource(leg.mode.transitModeNameString())
                    Text(
                        text = "$routeText ${leg.headsign ?: ""}".trim(),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2
                    )

                    // Agency
                    leg.agencyName?.let { agency ->
                        Text(
                            text = agency,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Duration
                Text(
                    text = formatDuration(leg.duration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Walking/Transit details
            when (leg.mode) {
                Mode.WALK, Mode.BIKE -> {
                    leg.distance?.let { distance ->
                        Text(
                            text = GeoUtils.formatDistance(distance, distanceUnit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                else -> {
                    // Show transfer info or other transit details
                    leg.intermediateStops?.size?.let { stops ->
                        Text(
                            text = pluralStringResource(plurals.transit_stop_count, stops, stops),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Add spacing before next leg
            if (!isLast) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // Add spacing between legs
    if (!isLast) {
        Spacer(modifier = Modifier.height(dimensionResource(dimen.padding)))
    }
}
