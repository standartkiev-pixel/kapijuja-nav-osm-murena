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

import java.util.Locale

object CountryCoordinateResolver {
    private val countryBounds = GeneratedCountryBounds.countryBounds

    fun resolve(latLng: LatLng): Address? {
        val countryCode = countryBounds.firstOrNull { it.contains(latLng) }?.countryCode ?: return null
        return Address(
            country = countryCode.displayCountryName(),
            countryCode = countryCode
        )
    }

    private fun String.displayCountryName(): String {
        return Locale.Builder()
            .setRegion(this)
            .build()
            .displayCountry
            .takeIf { it.isNotBlank() }
            ?: this
    }
}
