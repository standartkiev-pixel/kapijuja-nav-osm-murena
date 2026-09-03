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

package earth.maps.cardinal.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import earth.maps.cardinal.data.Place
import earth.maps.cardinal.data.PlaceIdGenerator

@Entity(tableName = "recent_searches")
data class RecentSearch(
    @PrimaryKey val id: String,  // UUID string
    val name: String,
    val description: String,
    val icon: String,
    val latitude: Double,
    val longitude: Double,
    // Address fields
    val houseNumber: String? = null,
    val road: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val tappedAt: Long
) {
    companion object {
        fun fromPlace(place: Place): RecentSearch {
            val timestamp = System.currentTimeMillis()

            return RecentSearch(
                id = PlaceIdGenerator.generateId(place),
                name = place.name,
                description = place.description,
                icon = place.icon,
                latitude = place.latLng.latitude,
                longitude = place.latLng.longitude,
                houseNumber = place.address?.houseNumber,
                road = place.address?.road,
                city = place.address?.city,
                state = place.address?.state,
                postcode = place.address?.postcode,
                country = place.address?.country,
                countryCode = place.address?.countryCode,
                tappedAt = timestamp,
            )
        }
    }
}
