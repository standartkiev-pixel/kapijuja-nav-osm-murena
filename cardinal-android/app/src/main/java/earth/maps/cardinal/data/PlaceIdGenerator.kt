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

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * A utility for generating deterministic, stable identifiers for geographical places.
 *
 * This generator uses a combination of coordinates and the place's display name to create
 * a SHA-256 hash. Using raw byte buffers for coordinates ensures that the ID remains
 * consistent across different device locales (avoiding decimal separator issues like '.' vs ',').
 *
 * Including the [name] in the generation process prevents ID collisions in scenarios where
 * multiple distinct points of interest (POIs) exist at the exact same coordinates.
 */
object PlaceIdGenerator {
    /**
     * The number of bytes required to store two Double values (Latitude and Longitude).
     */
    private const val COORD_BYTE_SIZE = 16

    /**
     * Generates a stable hex ID based on the provided location and name.
     *
     * @param latitude The latitude of the place.
     * @param longitude The longitude of the place.
     * @param name The display name or title of the place.
     * @return A deterministic SHA-256 hash string in hexadecimal format.
     */
    fun generateId(latitude: Double, longitude: Double, name: String): String {
        val nameBytes = name.toByteArray(Charsets.UTF_8)

        // Allocate space for coordinates (16 bytes) + name bytes
        val buffer = ByteBuffer.allocate(COORD_BYTE_SIZE + nameBytes.size)
        buffer.putDouble(latitude)
        buffer.putDouble(longitude)
        buffer.put(nameBytes)

        return hashBytesToHex(buffer.array())
    }

    /**
     * Returns a stable identifier for the given [place].
     *
     * If the [place] already contains a valid ID (e.g., from an online provider like Pelias),
     * that ID is returned. If the ID is null or blank (common with offline geocoding results),
     * a deterministic ID is generated based on the place's coordinates and name.
     *
     * @param place The place object to get or generate an ID for.
     * @return The existing ID if present, otherwise a generated SHA-256 hex string.
     */
    fun generateId(place: Place): String {
        return if (place.id.isNullOrBlank()) generateId(
            latitude = place.latLng.latitude,
            longitude = place.latLng.longitude,
            name = place.name
        ) else place.id
    }

    /**
     * Hashes a byte array using SHA-256 and converts it to a hex string.
     *
     * Implementation note: Uses [joinToString] for linear-time string construction
     * to avoid excessive memory allocations.
     */
    private fun hashBytesToHex(bytes: ByteArray): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}