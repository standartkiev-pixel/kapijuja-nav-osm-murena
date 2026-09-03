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

package earth.maps.cardinal.geocoding

import earth.maps.cardinal.data.GeocodeResult
import java.text.Normalizer
import java.util.Locale

internal class TrainStationResultClassifier {
    private val diacriticRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val trainStationCategoryValues = setOf(
        CATEGORY_TRANSPORTATION_TRAIN_STATION,
        CATEGORY_TRAIN_STATION,
        CATEGORY_RAILWAY_STATION
    )
    private val nonTrainMetadataValues = mapOf(
        PROPERTY_AMENITY to setOf("bus_station", "taxi", "fuel"),
        PROPERTY_HIGHWAY to setOf("bus_stop"),
        PROPERTY_RAILWAY to setOf("subway", "tram_stop"),
        PROPERTY_STATION to setOf("subway", "metro")
    )

    fun isTrainStation(result: GeocodeResult): Boolean {
        if (hasExplicitNonTrainMetadata(result)) {
            return false
        }
        if (hasTrainStationMetadata(result)) {
            return true
        }
        if (hasBroadTransportationCategory(result)) {
            return false
        }
        return false
    }

    private fun hasTrainStationMetadata(result: GeocodeResult): Boolean {
        return result.properties.any { (key, value) ->
            val normalizedKey = normalize(key)
            val normalizedValue = normalize(value)
            when (normalizedKey) {
                PROPERTY_RAILWAY -> normalizedValue in setOf(VALUE_STATION, VALUE_HALT)
                PROPERTY_STATION -> normalizedValue == VALUE_TRAIN
                PROPERTY_CATEGORY -> normalizedValue.split(',')
                    .map { category -> category.trim() }
                    .any { category -> category in trainStationCategoryValues }
                PROPERTY_PUBLIC_TRANSPORT -> normalizedValue == VALUE_STATION &&
                    result.hasPropertyValue(PROPERTY_STATION, VALUE_TRAIN)
                else -> false
            }
        }
    }

    private fun hasExplicitNonTrainMetadata(result: GeocodeResult): Boolean {
        return result.properties.any { (key, value) ->
            val normalizedKey = normalize(key)
            val normalizedValue = normalize(value)
            nonTrainMetadataValues[normalizedKey]?.contains(normalizedValue) == true
        }
    }

    private fun hasBroadTransportationCategory(result: GeocodeResult): Boolean {
        return result.properties.any { (key, value) ->
            normalize(key) == PROPERTY_CATEGORY &&
                normalize(value).split(',')
                    .map { category -> category.trim() }
                    .any { category -> category == CATEGORY_TRANSPORTATION }
        }
    }

    private fun GeocodeResult.hasPropertyValue(key: String, value: String): Boolean {
        val normalizedKey = normalize(key)
        val normalizedValue = normalize(value)
        return properties.any { (propertyKey, propertyValue) ->
            normalize(propertyKey) == normalizedKey && normalize(propertyValue) == normalizedValue
        }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(diacriticRegex, "")
            .lowercase(Locale.ROOT)
    }

    private companion object {
        private const val PROPERTY_AMENITY = "amenity"
        private const val PROPERTY_CATEGORY = "category"
        private const val PROPERTY_HIGHWAY = "highway"
        private const val PROPERTY_PUBLIC_TRANSPORT = "public_transport"
        private const val PROPERTY_RAILWAY = "railway"
        private const val PROPERTY_STATION = "station"

        private const val VALUE_HALT = "halt"
        private const val VALUE_STATION = "station"
        private const val VALUE_TRAIN = "train"

        private const val CATEGORY_RAILWAY_STATION = "railway_station"
        private const val CATEGORY_TRAIN_STATION = "train_station"
        private const val CATEGORY_TRANSPORTATION = "transportation"
        private const val CATEGORY_TRANSPORTATION_TRAIN_STATION = "transportation:train_station"
    }
}
