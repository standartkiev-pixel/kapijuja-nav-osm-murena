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

import android.net.Uri

data class ParsedGeoIntent(
    val name: String?,
    val latLng: LatLng,
)

object GeoIntentParser {
    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0

    fun parse(data: Uri): ParsedGeoIntent? {
        if (data.scheme != "geo") return null

        val query = data.safeQueryParameter("q")
        val queryCoordinateText = query
            ?.substringBefore('(')
            ?.takeIf { it.contains(',') }
        val coordinates = parseCoordinatePair(queryCoordinateText ?: data.geoCoordinateText())
            ?: return null

        if (query != null && queryCoordinateText == null && coordinates.isZeroCoordinate()) {
            return null
        }

        return ParsedGeoIntent(
            name = query.placeName(queryCoordinateText != null),
            latLng = coordinates
        )
    }

    private fun Uri.safeQueryParameter(name: String): String? {
        if (isHierarchical) {
            return getQueryParameter(name)
        }

        return encodedSchemeSpecificPart
            ?.substringAfter('?', missingDelimiterValue = "")
            ?.takeIf { it.isNotBlank() }
            ?.split('&')
            ?.firstNotNullOfOrNull { parameter ->
                val separatorIndex = parameter.indexOf('=')
                val encodedName = if (separatorIndex >= 0) {
                    parameter.substring(0, separatorIndex)
                } else {
                    parameter
                }
                if (Uri.decode(encodedName) != name) {
                    null
                } else if (separatorIndex >= 0) {
                    Uri.decode(parameter.substring(separatorIndex + 1))
                } else {
                    ""
                }
            }
    }

    private fun Uri.geoCoordinateText(): String {
        return host?.takeIf { it.contains(',') }
            ?: encodedSchemeSpecificPart
                ?.substringBefore('?')
                ?.let(Uri::decode)
                ?.removePrefix("//")
                .orEmpty()
    }

    private fun parseCoordinatePair(text: String): LatLng? {
        val parts = text.split(',', limit = 3)
        if (parts.size != 2) return null

        val latitude = parts[0].trim().toDoubleOrNull()
        val longitude = parts[1].trim().toDoubleOrNull()
        if (
            latitude != null &&
            longitude != null &&
            latitude in MIN_LATITUDE..MAX_LATITUDE &&
            longitude in MIN_LONGITUDE..MAX_LONGITUDE
        ) {
            return LatLng(latitude, longitude)
        }
        return null
    }

    private fun String?.placeName(queryContainsCoordinates: Boolean): String? {
        if (this == null) return null

        val label = substringAfter('(', missingDelimiterValue = "")
            .substringBeforeLast(')')
            .takeIf { it.isNotBlank() }
        return label ?: takeIf { !queryContainsCoordinates && it.isNotBlank() }
    }

    private fun LatLng.isZeroCoordinate(): Boolean {
        return latitude == 0.0 && longitude == 0.0
    }
}
