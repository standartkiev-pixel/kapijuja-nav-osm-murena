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

package earth.maps.cardinal.ui.directions

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

internal data class TransitDetailListInteractionState(
    val timelineListState: LazyListState,
    val summaryListState: LazyListState,
    val activeLegIndex: Int,
    val onLegSelected: (Int) -> Unit
)

@Composable
internal fun rememberTransitDetailListInteractionState(
    timelineItems: List<TransitTimelineLegUi>,
    summaryItems: List<TransitSummaryItemUi>,
    onLegClick: (Int) -> Unit
): TransitDetailListInteractionState {
    val timelineListState = rememberLazyListState()
    val summaryListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val activeLegIndex by remember(timelineItems) {
        derivedStateOf {
            val visibleIndex = timelineListState.firstVisibleItemIndex.coerceIn(
                0,
                timelineItems.lastIndex.coerceAtLeast(0)
            )
            timelineItems.getOrNull(visibleIndex)?.legIndex
                ?: timelineItems.firstOrNull()?.legIndex
                ?: 0
        }
    }

    LaunchedEffect(activeLegIndex, summaryItems) {
        val summaryIndex = summaryItems.indexOfLeg(activeLegIndex)
        if (summaryIndex >= 0) {
            summaryListState.animateScrollToItem(summaryIndex)
        }
    }

    val onLegSelected = remember(timelineItems, onLegClick) {
        { legIndex: Int ->
            coroutineScope.launch {
                onLegClick(legIndex)
                val timelineIndex = timelineItems.indexOfTimelineLeg(legIndex)
                if (timelineIndex >= 0) {
                    timelineListState.animateScrollToItem(timelineIndex)
                }
            }
            Unit
        }
    }

    return TransitDetailListInteractionState(
        timelineListState = timelineListState,
        summaryListState = summaryListState,
        activeLegIndex = activeLegIndex,
        onLegSelected = onLegSelected
    )
}
