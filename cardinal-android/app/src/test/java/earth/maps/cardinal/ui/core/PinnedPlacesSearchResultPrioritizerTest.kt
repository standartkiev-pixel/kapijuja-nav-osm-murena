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

package earth.maps.cardinal.ui.core

import earth.maps.cardinal.data.Address
import earth.maps.cardinal.data.LatLng
import earth.maps.cardinal.data.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedPlacesSearchResultPrioritizerTest {

    @Test
    fun `matching saved places are promoted before geocode results`() {
        val savedPlace = place(id = "saved-cafe", name = "Favorite Cafe")
        val geocodePlace = place(id = "geocode-park", name = "Favorite Park")

        val result = PinnedPlacesSearchResultPrioritizer.prioritize(
            query = "favorite",
            geocodePlaces = listOf(geocodePlace),
            pinnedPlaces = listOf(savedPlace)
        )

        assertEquals(listOf(savedPlace, geocodePlace), result)
    }

    @Test
    fun `non matching saved places are not added to search results`() {
        val savedPlace = place(id = "saved-cafe", name = "Favorite Cafe")
        val geocodePlace = place(id = "geocode-park", name = "City Park")

        val result = PinnedPlacesSearchResultPrioritizer.prioritize(
            query = "park",
            geocodePlaces = listOf(geocodePlace),
            pinnedPlaces = listOf(savedPlace)
        )

        assertEquals(listOf(geocodePlace), result)
    }

    @Test
    fun `duplicate geocode result is removed when saved place is promoted`() {
        val savedPlace = place(id = "saved-cafe", name = "Favorite Cafe")
        val duplicateGeocodePlace = place(id = "geocode-cafe", name = "Favorite Cafe")
        val otherGeocodePlace = place(
            id = "geocode-bookstore",
            name = "Favorite Bookstore",
            latitude = 2.0,
            longitude = 2.0
        )

        val result = PinnedPlacesSearchResultPrioritizer.prioritize(
            query = "favorite",
            geocodePlaces = listOf(duplicateGeocodePlace, otherGeocodePlace),
            pinnedPlaces = listOf(savedPlace)
        )

        assertEquals(listOf(savedPlace, otherGeocodePlace), result)
    }

    @Test
    fun `places with same coordinates and partially matching names are not deduplicated`() {
        val savedPlace = place(id = "saved-mahindra", name = "Mahindra")
        val geocodePlace = place(id = "geocode-mahindra-tractors", name = "Mahindra Tractors")

        val result = PinnedPlacesSearchResultPrioritizer.prioritize(
            query = "mahindra",
            geocodePlaces = listOf(geocodePlace),
            pinnedPlaces = listOf(savedPlace)
        )

        assertEquals(listOf(savedPlace, geocodePlace), result)
    }

    @Test
    fun `query can match saved place address fields with normalized text`() {
        val savedPlace = place(
            id = "saved-cosmos-bank",
            name = "The Cosmos Bank",
            address = Address(city = "Thane", road = "Ghodbunder Road")
        )
        val geocodePlace = place(id = "geocode-thane", name = "Thane")

        val result = PinnedPlacesSearchResultPrioritizer.prioritize(
            query = "thane",
            geocodePlaces = listOf(geocodePlace),
            pinnedPlaces = listOf(savedPlace)
        )

        assertEquals(listOf(savedPlace, geocodePlace), result)
    }

    @Test
    fun `matching saved and pinned places remain before train station provider results`() {
        val pinnedCafe = place(id = "pinned-cafe-lyon", name = "Café Lyonnais")
        val savedHotel = place(id = "saved-hotel-lyon", name = "Hôtel de Lyon")
        val trainStationPlace = place(id = "geocode-gare-de-lyon", name = "Gare de Lyon")
        val cityCentrePlace = place(id = "geocode-centre-ville-lyon", name = "Centre-ville de Lyon")

        val result = PinnedPlacesSearchResultPrioritizer.prioritize(
            query = "lyon",
            geocodePlaces = listOf(trainStationPlace, cityCentrePlace),
            pinnedPlaces = listOf(pinnedCafe, savedHotel)
        )

        assertEquals(
            listOf(pinnedCafe, savedHotel, trainStationPlace, cityCentrePlace),
            result
        )
    }

    private fun place(
        id: String,
        name: String,
        latitude: Double = 1.0,
        longitude: Double = 1.0,
        address: Address? = null,
    ): Place {
        return Place(
            id = id,
            name = name,
            latLng = LatLng(latitude, longitude),
            address = address
        )
    }
}
