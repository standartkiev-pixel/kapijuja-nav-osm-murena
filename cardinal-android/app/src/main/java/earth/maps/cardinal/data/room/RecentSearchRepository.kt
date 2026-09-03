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

import earth.maps.cardinal.data.Address
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentSearchRepository @Inject constructor(
    database: AppDatabase,
) {
    private val searchDao = database.recentSearchDao()

    companion object {
        const val MAX_RECENT_SEARCHES = 20
    }

    /**
     * Adds a recent search and ensures we don't exceed the maximum count.
     */
    suspend fun addRecentSearch(place: Place): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (place.isMyLocation) {
                return@withContext Result.success(Unit)
            }
            val recentSearch = RecentSearch.fromPlace(place)
            searchDao.insertSearch(recentSearch)
            searchDao.deleteOldSearches(MAX_RECENT_SEARCHES)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts a RecentSearch back to a Place for UI consumption.
     */
    fun toPlace(recentSearch: RecentSearch): Place {
        return Place(
            id = recentSearch.id,
            name = recentSearch.name,
            description = recentSearch.description,
            icon = recentSearch.icon,
            latLng = LatLng(
                latitude = recentSearch.latitude, longitude = recentSearch.longitude
            ),
            address = if (recentSearch.houseNumber != null || recentSearch.road != null || recentSearch.city != null || recentSearch.state != null || recentSearch.postcode != null || recentSearch.country != null || recentSearch.countryCode != null) {
                Address(
                    houseNumber = recentSearch.houseNumber,
                    road = recentSearch.road,
                    city = recentSearch.city,
                    state = recentSearch.state,
                    postcode = recentSearch.postcode,
                    country = recentSearch.country,
                    countryCode = recentSearch.countryCode
                )
            } else {
                null
            }
        )
    }

    /**
     * Gets recent searches with an optional limit (defaults to 10 for UI display).
     */
    fun getRecentSearches(limit: Int = 10): Flow<List<RecentSearch>> {
        return searchDao.getRecentSearches().map { list ->
            list.distinctBy { it.copy(id = "", tappedAt = 0) }.take(limit)
        }
    }

    /**
     * Remove a a RecentSearch from the database, along with all duplicates that may have different IDs or timestamps.
     */
    suspend fun removeRecentSearch(searchToDelete: RecentSearch) {
        searchDao.deleteSearch(searchToDelete)

        // A subtle point: There may be duplicates filtered out by the
        // distinctBy logic above, and they should be removed too.
        searchDao.getRecentSearches().firstOrNull()?.filter {
            it.copy(id = "", tappedAt = 0) == searchToDelete.copy(id = "", tappedAt = 0)
        }?.forEach {
            searchDao.deleteSearch(it)
        }
    }

}
