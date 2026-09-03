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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import earth.maps.cardinal.R.dimen
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.transit.Itinerary
import earth.maps.cardinal.ui.navigation.CurrentSpeedViewModel

@Composable
fun TransitItineraryDetailScreen(
    itinerary: Itinerary,
    onBack: () -> Unit,
    onLegClick: (Int) -> Unit = {},
    appPreferences: AppPreferenceRepository
) {
    BackHandler {
        onBack()
    }

    val use24HourFormat by appPreferences.use24HourFormat.collectAsStateWithLifecycle()
    val distanceUnit by appPreferences.distanceUnit.collectAsStateWithLifecycle()
    val currentSpeedViewModel: CurrentSpeedViewModel = hiltViewModel()
    val currentSpeed by currentSpeedViewModel.currentSpeed.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val textProvider = remember(context) {
        AndroidTransitItineraryDetailTextProvider(context)
    }
    val presenter = remember(textProvider) {
        TransitItineraryDetailPresenter(textProvider)
    }
    val detailUi = remember(itinerary, use24HourFormat, distanceUnit, presenter) {
        presenter.present(
            itinerary = itinerary,
            use24HourFormat = use24HourFormat,
            distanceUnit = distanceUnit
        )
    }
    val currentSpeedText = currentSpeed?.displayText
    val overview = remember(detailUi.overview, itinerary, use24HourFormat, distanceUnit, currentSpeedText, presenter) {
        if (currentSpeedText == null) {
            detailUi.overview
        } else {
            presenter.presentOverview(
                itinerary = itinerary,
                use24HourFormat = use24HourFormat,
                distanceUnit = distanceUnit,
                currentSpeedText = currentSpeedText
            )
        }
    }
    val displayUi = remember(detailUi, overview) {
        detailUi.copy(overview = overview)
    }
    val listInteractionState = rememberTransitDetailListInteractionState(
        timelineItems = detailUi.timelineItems,
        summaryItems = detailUi.summaryItems,
        onLegClick = onLegClick
    )

    TransitItineraryDetailContent(
        detailUi = displayUi,
        listInteractionState = listInteractionState,
        onBack = onBack
    )
}

@Composable
private fun TransitItineraryDetailContent(
    detailUi: TransitItineraryDetailUi,
    listInteractionState: TransitDetailListInteractionState,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensionResource(dimen.padding))
    ) {
        TransitDetailHeader(
            overview = detailUi.overview,
            summaryItems = detailUi.summaryItems,
            activeLegIndex = listInteractionState.activeLegIndex,
            summaryListState = listInteractionState.summaryListState,
            onBack = onBack,
            onLegSelected = listInteractionState.onLegSelected
        )

        Spacer(modifier = Modifier.height(dimensionResource(dimen.padding)))

        TransitDetailTimeline(
            timelineItems = detailUi.timelineItems,
            activeLegIndex = listInteractionState.activeLegIndex,
            listState = listInteractionState.timelineListState,
            onLegClick = listInteractionState.onLegSelected,
            modifier = Modifier.weight(1f)
        )
    }
}
