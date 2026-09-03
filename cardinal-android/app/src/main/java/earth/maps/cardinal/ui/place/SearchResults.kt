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

package earth.maps.cardinal.ui.place

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import earth.maps.cardinal.R
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.R.drawable
import earth.maps.cardinal.data.AddressFormatter
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.format

@Composable
fun SearchResults(
    places: List<Place>,
    onPlaceSelected: (Place) -> Unit,
    expandedResultsAvailable: Boolean,
    onShowExpandedResults: () -> Unit,
    modifier: Modifier = Modifier
) {
    val addressFormatter = remember { AddressFormatter() }
    LazyColumn(modifier = modifier) {
        itemsIndexed(places) { index, place ->
            if (index != 0) {
                HorizontalDivider(modifier = Modifier.fillMaxWidth())
            }
            SearchResultItem(
                addressFormatter = addressFormatter,
                place = place,
                onPlaceSelected = onPlaceSelected
            )
        }
        if (places.isEmpty() && expandedResultsAvailable) {
            item {
                ExpandSearchResultsCard(onShowExpandedResults)
            }
        }
    }
}

@Composable
fun ExpandSearchResultsCard(
    onShowExpandedResults: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(dimen.padding))
            .clickable {
                onShowExpandedResults()
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(dimen.padding)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Re-run search.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = dimensionResource(dimen.padding))
            ) {
                Text(
                    text = stringResource(R.string.search_again),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.search_again_details),
                )
            }
        }
    }

}

@Composable
fun SearchResultItem(
    addressFormatter: AddressFormatter,
    place: Place,
    onPlaceSelected: (Place) -> Unit,
    onRemoveTapped: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = dimensionResource(dimen.padding))
            .clickable {
                // Convert GeocodeResult to Place with a unique ID based on properties
                onPlaceSelected(place)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensionResource(dimen.padding)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search result icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(dimensionResource(dimen.padding) / 2),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(drawable.ic_search),
                    contentDescription = null,
                    modifier = Modifier.size(dimensionResource(dimen.icon_size))
                )
            }

            // Search result details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = dimensionResource(dimen.padding))
            ) {
                Text(
                    text = place.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                place.address?.format(addressFormatter)?.let { address ->
                    Text(
                        text = address.trim().replace("\n", ", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (place.isSaved) {
                Icon(
                    painter = painterResource(drawable.ic_bookmark_star),
                    contentDescription = stringResource(R.string.saved_place_result),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = dimensionResource(dimen.padding_minor))
                        .size(14.dp)
                )
            }

            onRemoveTapped?.let { onRemoveTapped ->
                IconButton(onClick = onRemoveTapped) {
                    Icon(
                        painter = painterResource(drawable.ic_close),
                        contentDescription = stringResource(
                            R.string.remove_recent_search
                        )
                    )
                }
            }
        }
    }
}
