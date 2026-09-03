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

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.room.SavedPlaceRepository
import javax.inject.Inject

@HiltViewModel
class PlaceCardViewModel @Inject constructor(
    private val savedPlaceRepository: SavedPlaceRepository,
) : ViewModel() {

    val isPlaceSaved = mutableStateOf(false)
    val place = mutableStateOf<Place?>(null)

    suspend fun setPlace(place: Place) {
        this.place.value = place
        checkIfPlaceIsSaved(place)
    }

    suspend fun checkIfPlaceIsSaved(place: Place) {
        if (place.id != null) {
            val existingPlace = savedPlaceRepository.getPlaceById(place.id).getOrNull()
            isPlaceSaved.value = existingPlace != null
        }
    }

    suspend fun savePlace(place: Place) {
        savedPlaceRepository.savePlace(place)
        isPlaceSaved.value = true
    }

    suspend fun unsavePlace(place: Place) {
        place.id?.let { id ->
            savedPlaceRepository.deletePlace(placeId = id)
        }
        isPlaceSaved.value = false
    }
}
