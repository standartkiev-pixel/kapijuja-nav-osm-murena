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

package earth.maps.cardinal.data

import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PlaceIdGeneratorTest {

    @Test
    fun `generateId is deterministic for the same input`() {
        val lat = 19.1136
        val lon = 72.8697
        val name = "JB Nagar"

        val id1 = PlaceIdGenerator.generateId(lat, lon, name)
        val id2 = PlaceIdGenerator.generateId(lat, lon, name)

        assertEquals("Same input must produce the same ID", id1, id2)
    }

    @Test
    fun `generateId produces different IDs for different names at same coordinates`() {
        val lat = 19.1136
        val lon = 72.8697

        val id1 = PlaceIdGenerator.generateId(lat, lon, "Coffee Shop")
        val id2 = PlaceIdGenerator.generateId(lat, lon, "Law Firm")

        assertNotEquals("Different names at same coordinates must produce different IDs", id1, id2)
    }

    @Test
    fun `generateId produces different IDs for different coordinates with same name`() {
        val name = "Starbucks"

        val id1 = PlaceIdGenerator.generateId(19.1136, 72.8697, name)
        val id2 = PlaceIdGenerator.generateId(18.9218, 72.8347, name)

        assertNotEquals("Same name at different coordinates must produce different IDs", id1, id2)
    }

    @Test
    fun `generateId is locale independent`() {
        val lat = 19.1136
        val lon = 72.8697
        val name = "JB Nagar"

        // Store original locale
        val originalLocale = Locale.getDefault()

        try {
            // Set locale to US (uses '.' as decimal separator)
            Locale.setDefault(Locale.US)
            val idUS = PlaceIdGenerator.generateId(lat, lon, name)

            // Set locale to GERMANY (uses ',' as decimal separator)
            Locale.setDefault(Locale.GERMANY)
            val idGermany = PlaceIdGenerator.generateId(lat, lon, name)

            assertEquals(
                "ID must be identical regardless of system decimal separators",
                idUS,
                idGermany
            )
        } finally {
            // Restore locale to avoid affecting other tests
            Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `generateId handles special characters and emojis in name`() {
        val lat = 0.0
        val lon = 0.0
        val name1 = "Müchen Café ☕"
        val name2 = "Müchen Café ☕"

        val id1 = PlaceIdGenerator.generateId(lat, lon, name1)
        val id2 = PlaceIdGenerator.generateId(lat, lon, name2)

        assertEquals("Special characters and emojis should be handled deterministically", id1, id2)
    }

    @Test
    fun `generateId produces a valid 64 character SHA-256 hex string`() {
        val id = PlaceIdGenerator.generateId(1.0, 1.0, "Test")

        // SHA-256 produces 32 bytes, which is 64 hex characters
        assertEquals("ID length should be 64 characters", 64, id.length)

        // Verify it only contains hex characters
        val hexRegex = Regex("^[0-9a-fA-F]+$")
        assertTrue(id.matches(hexRegex))
    }

    @Test
    fun `generateId with existing ID returns the original ID`() {
        val existingId = "osm:way:12345"
        val place = mockk<Place> {
            every { id } returns existingId
        }

        val result = PlaceIdGenerator.generateId(place)

        assertEquals("Should return the ID already present in the Place object", existingId, result)
    }

    @Test
    fun `generateId with null or blank ID generates a deterministic hash`() {
        val latitude = 45.523062
        val longitude = -122.676482
        val placeName = "Pioneer Courthouse Square"

        val place = mockk<Place> {
            every { id } returns "" // or null
            every { latLng } returns LatLng(latitude, longitude)
            every { name } returns placeName
        }

        val result = PlaceIdGenerator.generateId(place)

        // Calculate expected hash using the existing primitive function to ensure parity
        val expected = PlaceIdGenerator.generateId(latitude, longitude, placeName)

        assertEquals("Generated ID should match the hash of coordinates and name", expected, result)
        assertNotEquals("Result should not be blank", "", result)
    }

    @Test
    fun `generateId is stable for identical offline places`() {
        val lat = 52.5200
        val lon = 13.4050
        val placeName = "Berlin TV Tower"

        val place1 = mockk<Place> {
            every { id } returns null
            every { latLng } returns LatLng(lat, lon)
            every { name } returns placeName
        }

        val place2 = mockk<Place> {
            every { id } returns ""
            every { latLng } returns LatLng(lat, lon)
            every { name } returns placeName
        }

        assertEquals(
            "Two different place objects with same data should produce the same ID",
            PlaceIdGenerator.generateId(place1),
            PlaceIdGenerator.generateId(place2)
        )
    }

    @Test
    fun `generateId differs when coordinates or name change`() {
        val lat = 40.7128
        val lon = -74.0060

        val placeA = mockk<Place> {
            every { id } returns null
            every { latLng } returns LatLng(lat, lon)
            every { name } returns "Location A"
        }

        val placeB = mockk<Place> {
            every { id } returns null
            every { latLng } returns LatLng(lat, lon)
            every { name } returns "Location B"
        }

        assertNotEquals(
            "Places at the same coordinates but with different names should have different IDs",
            PlaceIdGenerator.generateId(placeA),
            PlaceIdGenerator.generateId(placeB)
        )
    }

}