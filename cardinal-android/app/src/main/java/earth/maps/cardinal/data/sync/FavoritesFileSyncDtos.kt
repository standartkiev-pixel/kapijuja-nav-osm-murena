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

package earth.maps.cardinal.data.sync

import earth.maps.cardinal.data.room.ItemType
import earth.maps.cardinal.data.room.ListItem
import earth.maps.cardinal.data.room.SavedList
import earth.maps.cardinal.data.room.SavedPlace
import kotlinx.serialization.Serializable

@Serializable
data class FavoritesSyncFileDto(
    val schemaVersion: Int = FAVORITES_SYNC_SCHEMA_VERSION,
    val updatedAt: Long,
    val lists: List<FavoritesSyncListDto>,
    val places: List<FavoritesSyncPlaceDto>,
    val listItems: List<FavoritesSyncListItemDto>
)

@Serializable
data class FavoritesSyncListDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val isRoot: Boolean = false,
    val isCollapsed: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class FavoritesSyncPlaceDto(
    val id: String,
    val placeId: Int? = null,
    val customName: String? = null,
    val customDescription: String? = null,
    val isPinned: Boolean = false,
    val name: String,
    val type: String,
    val icon: String,
    val latitude: Double,
    val longitude: Double,
    val houseNumber: String? = null,
    val road: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val openingHours: String? = null,
    val isTransitStop: Boolean = false,
    val transitStopId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class FavoritesSyncListItemDto(
    val listId: String,
    val itemId: String,
    val itemType: ItemType,
    val position: Int,
    val addedAt: Long
)

fun SavedList.toFavoritesSyncDto(): FavoritesSyncListDto {
    return FavoritesSyncListDto(
        id = id,
        name = name,
        description = description,
        isRoot = isRoot,
        isCollapsed = isCollapsed,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun FavoritesSyncListDto.toSavedList(): SavedList {
    return SavedList(
        id = id,
        name = name,
        description = description,
        isRoot = isRoot,
        isCollapsed = isCollapsed,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@Suppress("DEPRECATION")
fun SavedPlace.toFavoritesSyncDto(): FavoritesSyncPlaceDto {
    return FavoritesSyncPlaceDto(
        id = id,
        placeId = placeId,
        customName = customName,
        customDescription = customDescription,
        isPinned = isPinned,
        name = name,
        type = type,
        icon = icon,
        latitude = latitude,
        longitude = longitude,
        houseNumber = houseNumber,
        road = road,
        city = city,
        state = state,
        postcode = postcode,
        country = country,
        countryCode = countryCode,
        openingHours = openingHours,
        isTransitStop = isTransitStop,
        transitStopId = transitStopId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun FavoritesSyncPlaceDto.toSavedPlace(): SavedPlace {
    return SavedPlace(
        id = id,
        placeId = placeId,
        customName = customName,
        customDescription = customDescription,
        isPinned = isPinned,
        name = name,
        type = type,
        icon = icon,
        latitude = latitude,
        longitude = longitude,
        houseNumber = houseNumber,
        road = road,
        city = city,
        state = state,
        postcode = postcode,
        country = country,
        countryCode = countryCode,
        openingHours = openingHours,
        isTransitStop = isTransitStop,
        transitStopId = transitStopId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun ListItem.toFavoritesSyncDto(): FavoritesSyncListItemDto {
    return FavoritesSyncListItemDto(
        listId = listId,
        itemId = itemId,
        itemType = itemType,
        position = position,
        addedAt = addedAt
    )
}

fun FavoritesSyncListItemDto.toListItem(): ListItem {
    return ListItem(
        listId = listId,
        itemId = itemId,
        itemType = itemType,
        position = position,
        addedAt = addedAt
    )
}

const val FAVORITES_SYNC_SCHEMA_VERSION = 1
