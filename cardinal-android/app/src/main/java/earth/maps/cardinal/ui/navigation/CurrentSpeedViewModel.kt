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

package earth.maps.cardinal.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import earth.maps.cardinal.data.AppPreferenceRepository
import earth.maps.cardinal.data.SpeedRepository
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class CurrentSpeedViewModel @Inject constructor(
    appPreferences: AppPreferenceRepository,
    speedRepository: SpeedRepository
) : ViewModel() {

    val currentSpeed = combine(
        speedRepository.speedFlow,
        appPreferences.distanceUnit,
        currentTimeMillisTicker()
    ) { speed, distanceUnit, currentTimeMillis ->
        speed.toCurrentSpeedUi(
            distanceUnit = distanceUnit,
            nowMillis = currentTimeMillis
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private fun currentTimeMillisTicker(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CURRENT_SPEED_STALE_MILLIS)
        }
    }
}
