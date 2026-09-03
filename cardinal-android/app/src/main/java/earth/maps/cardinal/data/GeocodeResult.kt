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

package earth.maps.cardinal.data

import kotlin.math.abs

data class GeocodeResult(
    val geocodeId: String,
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val properties: Map<String, String>,
    val address: Address? = null,
) {
    companion object {
        /**
         * Generate a unique ID for a place based on its properties.
         * This ensures that each search result gets a consistent but unique ID.
         */
        fun generatePlaceId(result: GeocodeResult): Int {
            // Create a string representation of the unique properties
            val uniqueString = buildString {
                append(result.latitude)
                append(result.longitude)
                append(result.displayName)
            }

            // Generate a hash code and ensure it's positive
            return abs(uniqueString.hashCode())
        }
    }
}

data class Address(
    val houseNumber: String? = null,
    val road: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postcode: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
) {
    companion object {
        const val TAG = "Address"
    }
}

fun Address.format(formatter: AddressFormatter, includeCountry: Boolean = true): String? {
    return formatter.format(this, includeCountry)
}
